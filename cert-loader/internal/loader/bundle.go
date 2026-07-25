package loader

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/sha256"
	"crypto/x509"
	"encoding/asn1"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"io"
	"reflect"
	"slices"
	"time"
)

type tlsBundle struct {
	SchemaVersion  int    `json:"schemaVersion"`
	Environment    string `json:"environment"`
	Service        string `json:"service"`
	SPIFFEID       string `json:"spiffeId"`
	CertificatePEM string `json:"certificatePem"`
	PrivateKeyPEM  string `json:"privateKeyPem"`
	CABundlePEM    string `json:"caBundlePem"`
	NotBefore      string `json:"notBefore"`
	NotAfter       string `json:"notAfter"`
}

type Material struct {
	Certificate []byte
	PrivateKey  []byte
	CABundle    []byte
}

type Metadata struct {
	Service           string
	SPIFFEID          string
	FingerprintSHA256 string
	NotAfter          time.Time
	RotationRequired  bool
	VersionID         string
}

func ValidateBundle(
	payload []byte,
	contract ServiceContract,
	now time.Time,
) (Material, Metadata, error) {
	if len(payload) == 0 || len(payload) > maxSecretBytes {
		return Material{}, Metadata{}, fail("bundle_invalid", nil)
	}
	decoder := json.NewDecoder(bytes.NewReader(payload))
	decoder.DisallowUnknownFields()
	var bundle tlsBundle
	if err := decoder.Decode(&bundle); err != nil {
		return Material{}, Metadata{}, fail("bundle_invalid", err)
	}
	if err := ensureJSONEnd(decoder); err != nil {
		return Material{}, Metadata{}, fail("bundle_invalid", err)
	}
	if bundle.SchemaVersion != 1 ||
		bundle.Environment != Environment ||
		bundle.Service != contract.Service ||
		bundle.SPIFFEID != contract.SPIFFEID ||
		bundle.CertificatePEM == "" ||
		bundle.PrivateKeyPEM == "" ||
		bundle.CABundlePEM == "" ||
		bundle.NotBefore == "" ||
		bundle.NotAfter == "" {
		return Material{}, Metadata{}, fail("bundle_invalid", nil)
	}

	certificateBlocks, err := parsePEM([]byte(bundle.CertificatePEM), "CERTIFICATE")
	if err != nil || len(certificateBlocks) != 2 {
		return Material{}, Metadata{}, fail("certificate_invalid", err)
	}
	caBlocks, err := parsePEM([]byte(bundle.CABundlePEM), "CERTIFICATE")
	if err != nil || (len(caBlocks) != 2 && len(caBlocks) != 4) {
		return Material{}, Metadata{}, fail("ca_bundle_invalid", err)
	}
	keyBlocks, err := parsePEM([]byte(bundle.PrivateKeyPEM), "PRIVATE KEY")
	if err != nil || len(keyBlocks) != 1 {
		return Material{}, Metadata{}, fail("private_key_invalid", err)
	}

	leaf, err := x509.ParseCertificate(certificateBlocks[0])
	if err != nil {
		return Material{}, Metadata{}, fail("certificate_invalid", err)
	}
	presentedIntermediate, err := x509.ParseCertificate(certificateBlocks[1])
	if err != nil {
		return Material{}, Metadata{}, fail("certificate_invalid", err)
	}
	caPairs := make([][2]*x509.Certificate, 0, len(caBlocks)/2)
	for index := 0; index < len(caBlocks); index += 2 {
		pairIntermediate, err := x509.ParseCertificate(caBlocks[index])
		if err != nil {
			return Material{}, Metadata{}, fail("ca_bundle_invalid", err)
		}
		pairRoot, err := x509.ParseCertificate(caBlocks[index+1])
		if err != nil {
			return Material{}, Metadata{}, fail("ca_bundle_invalid", err)
		}
		caPairs = append(caPairs, [2]*x509.Certificate{pairIntermediate, pairRoot})
	}
	if len(caPairs) == 2 &&
		(bytes.Equal(caPairs[0][0].Raw, caPairs[1][0].Raw) ||
			bytes.Equal(caPairs[0][1].Raw, caPairs[1][1].Raw)) {
		return Material{}, Metadata{}, fail("ca_bundle_invalid", nil)
	}

	privateKeyValue, err := x509.ParsePKCS8PrivateKey(keyBlocks[0])
	if err != nil {
		return Material{}, Metadata{}, fail("private_key_invalid", err)
	}
	privateKey, ok := privateKeyValue.(*ecdsa.PrivateKey)
	if !ok || privateKey.Curve != elliptic.P256() {
		return Material{}, Metadata{}, fail("private_key_invalid", nil)
	}

	var intermediate, root *x509.Certificate
	for _, pair := range caPairs {
		pairIntermediate, pairRoot := pair[0], pair[1]
		if err := validateCA(pairRoot, 1, false, now); err != nil {
			return Material{}, Metadata{}, err
		}
		if err := validateCA(pairIntermediate, 0, true, now); err != nil {
			return Material{}, Metadata{}, err
		}
		if err := pairRoot.CheckSignatureFrom(pairRoot); err != nil {
			return Material{}, Metadata{}, fail("chain_invalid", err)
		}
		if err := pairIntermediate.CheckSignatureFrom(pairRoot); err != nil {
			return Material{}, Metadata{}, fail("chain_invalid", err)
		}
		if bytes.Equal(presentedIntermediate.Raw, pairIntermediate.Raw) {
			intermediate = pairIntermediate
			root = pairRoot
		}
	}
	if intermediate == nil {
		return Material{}, Metadata{}, fail("certificate_invalid", nil)
	}
	if err := validateLeaf(leaf, privateKey, contract, bundle, now); err != nil {
		return Material{}, Metadata{}, err
	}
	if err := verifyChain(leaf, intermediate, root, contract, now); err != nil {
		return Material{}, Metadata{}, err
	}

	fingerprint := sha256.Sum256(leaf.Raw)
	material := Material{
		Certificate: []byte(bundle.CertificatePEM),
		PrivateKey:  []byte(bundle.PrivateKeyPEM),
		CABundle:    []byte(bundle.CABundlePEM),
	}
	metadata := Metadata{
		Service:           contract.Service,
		SPIFFEID:          contract.SPIFFEID,
		FingerprintSHA256: hex.EncodeToString(fingerprint[:]),
		NotAfter:          leaf.NotAfter.UTC(),
		RotationRequired:  leaf.NotAfter.Sub(now) <= rotationDays*24*time.Hour,
	}
	return material, metadata, nil
}

func ensureJSONEnd(decoder *json.Decoder) error {
	var trailing any
	err := decoder.Decode(&trailing)
	if err == io.EOF {
		return nil
	}
	if err == nil {
		return errors.New("trailing JSON value")
	}
	return err
}

func parsePEM(data []byte, expectedType string) ([][]byte, error) {
	var blocks [][]byte
	remaining := bytes.TrimSpace(data)
	for len(remaining) > 0 {
		block, rest := pem.Decode(remaining)
		if block == nil || block.Type != expectedType || len(block.Headers) != 0 {
			return nil, fail("pem_invalid", nil)
		}
		blocks = append(blocks, block.Bytes)
		remaining = bytes.TrimSpace(rest)
	}
	if len(blocks) == 0 {
		return nil, fail("pem_invalid", nil)
	}
	return blocks, nil
}

func validateCA(
	certificate *x509.Certificate,
	pathLength int,
	pathLengthZero bool,
	now time.Time,
) error {
	if !certificate.IsCA ||
		!certificate.BasicConstraintsValid ||
		certificate.MaxPathLen != pathLength ||
		certificate.MaxPathLenZero != pathLengthZero ||
		certificate.KeyUsage != x509.KeyUsageCertSign|x509.KeyUsageCRLSign ||
		certificate.SignatureAlgorithm != x509.ECDSAWithSHA256 ||
		!isP256(certificate.PublicKey) ||
		now.Before(certificate.NotBefore) ||
		!now.Before(certificate.NotAfter) {
		return fail("ca_bundle_invalid", nil)
	}
	return nil
}

func validateLeaf(
	leaf *x509.Certificate,
	privateKey *ecdsa.PrivateKey,
	contract ServiceContract,
	bundle tlsBundle,
	now time.Time,
) error {
	if leaf.IsCA ||
		!leaf.BasicConstraintsValid ||
		leaf.KeyUsage != x509.KeyUsageDigitalSignature ||
		leaf.SignatureAlgorithm != x509.ECDSAWithSHA256 ||
		!isP256(leaf.PublicKey) ||
		!privateKey.PublicKey.Equal(leaf.PublicKey) {
		return fail("certificate_invalid", nil)
	}
	if now.Before(leaf.NotBefore) {
		return fail("certificate_not_yet_valid", nil)
	}
	if !now.Before(leaf.NotAfter) {
		return fail("certificate_expired", nil)
	}
	if leaf.NotAfter.Sub(leaf.NotBefore) > leafValidityDays*24*time.Hour+5*time.Minute {
		return fail("certificate_invalid", nil)
	}
	if bundle.NotBefore != leaf.NotBefore.UTC().Format(time.RFC3339) ||
		bundle.NotAfter != leaf.NotAfter.UTC().Format(time.RFC3339) {
		return fail("bundle_invalid", nil)
	}
	if err := validateSANs(leaf, contract); err != nil {
		return err
	}
	if err := validateEKUs(leaf, contract); err != nil {
		return err
	}
	return nil
}

func validateSANs(leaf *x509.Certificate, contract ServiceContract) error {
	if len(leaf.URIs) != 1 ||
		leaf.URIs[0].String() != contract.SPIFFEID ||
		!reflect.DeepEqual(leaf.DNSNames, contract.DNSSANs) ||
		len(leaf.IPAddresses) != 0 ||
		len(leaf.EmailAddresses) != 0 {
		return fail("identity_invalid", nil)
	}
	for _, dnsName := range leaf.DNSNames {
		if stringsContainsWildcard(dnsName) {
			return fail("identity_invalid", nil)
		}
	}

	var sanExtension []byte
	for _, extension := range leaf.Extensions {
		if extension.Id.Equal([]int{2, 5, 29, 17}) {
			sanExtension = extension.Value
			break
		}
	}
	var sequence asn1.RawValue
	rest, err := asn1.Unmarshal(sanExtension, &sequence)
	if err != nil || len(rest) != 0 || sequence.Tag != asn1.TagSequence {
		return fail("identity_invalid", err)
	}
	var rawDNS []string
	var rawURIs []string
	values := sequence.Bytes
	for len(values) > 0 {
		var value asn1.RawValue
		values, err = asn1.Unmarshal(values, &value)
		if err != nil || value.Class != asn1.ClassContextSpecific {
			return fail("identity_invalid", err)
		}
		switch value.Tag {
		case 2:
			rawDNS = append(rawDNS, string(value.Bytes))
		case 6:
			rawURIs = append(rawURIs, string(value.Bytes))
		default:
			return fail("identity_invalid", nil)
		}
	}
	if !reflect.DeepEqual(rawDNS, contract.DNSSANs) ||
		!reflect.DeepEqual(rawURIs, []string{contract.SPIFFEID}) {
		return fail("identity_invalid", nil)
	}
	return nil
}

func stringsContainsWildcard(value string) bool {
	return bytes.ContainsRune([]byte(value), '*')
}

func validateEKUs(leaf *x509.Certificate, contract ServiceContract) error {
	if len(leaf.UnknownExtKeyUsage) != 0 {
		return fail("eku_invalid", nil)
	}
	expected := make([]x509.ExtKeyUsage, 0, len(contract.EKUs))
	for _, usage := range contract.EKUs {
		switch usage {
		case "clientAuth":
			expected = append(expected, x509.ExtKeyUsageClientAuth)
		case "serverAuth":
			expected = append(expected, x509.ExtKeyUsageServerAuth)
		default:
			return fail("eku_invalid", nil)
		}
	}
	if !slices.Equal(leaf.ExtKeyUsage, expected) {
		return fail("eku_invalid", nil)
	}
	return nil
}

func verifyChain(
	leaf, intermediate, root *x509.Certificate,
	contract ServiceContract,
	now time.Time,
) error {
	roots := x509.NewCertPool()
	roots.AddCert(root)
	intermediates := x509.NewCertPool()
	intermediates.AddCert(intermediate)
	keyUsages := make([]x509.ExtKeyUsage, 0, len(contract.EKUs))
	for _, usage := range contract.EKUs {
		if usage == "clientAuth" {
			keyUsages = append(keyUsages, x509.ExtKeyUsageClientAuth)
		} else {
			keyUsages = append(keyUsages, x509.ExtKeyUsageServerAuth)
		}
	}
	if _, err := leaf.Verify(x509.VerifyOptions{
		Roots:         roots,
		Intermediates: intermediates,
		CurrentTime:   now,
		KeyUsages:     keyUsages,
	}); err != nil {
		return fail("chain_invalid", err)
	}
	return nil
}

func isP256(publicKey any) bool {
	key, ok := publicKey.(*ecdsa.PublicKey)
	return ok && key.Curve == elliptic.P256()
}
