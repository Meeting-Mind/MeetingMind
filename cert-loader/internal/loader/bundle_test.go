package loader

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/asn1"
	"encoding/json"
	"encoding/pem"
	"math/big"
	"net"
	"net/url"
	"strings"
	"testing"
	"time"
)

func TestValidateBundleAcceptsAllServiceContracts(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	for service, serviceContract := range serviceContracts {
		service := service
		serviceContract := serviceContract
		t.Run(service, func(t *testing.T) {
			t.Parallel()
			payload := testBundle(t, serviceContract, now, nil)
			material, metadata, err := ValidateBundle(payload, serviceContract, now)
			if err != nil {
				t.Fatalf("ValidateBundle() error = %v", err)
			}
			if len(material.Certificate) == 0 ||
				len(material.PrivateKey) == 0 ||
				len(material.CABundle) == 0 {
				t.Fatal("ValidateBundle() returned incomplete material")
			}
			if metadata.Service != serviceContract.Service ||
				metadata.SPIFFEID != serviceContract.SPIFFEID ||
				len(metadata.FingerprintSHA256) != 64 ||
				metadata.RotationRequired {
				t.Fatalf("unexpected metadata: %#v", metadata)
			}
		})
	}
}

func TestValidateBundleMarksRotationWindow(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["auth"]
	payload := testBundle(t, serviceContract, now, func(leaf *x509.Certificate) {
		leaf.NotAfter = now.Add(30 * 24 * time.Hour)
	})
	_, metadata, err := ValidateBundle(payload, serviceContract, now)
	if err != nil {
		t.Fatalf("ValidateBundle() error = %v", err)
	}
	if !metadata.RotationRequired {
		t.Fatal("certificate at the 30-day boundary must require rotation")
	}
}

func TestValidateBundleRejectsInvalidIdentityValidityAndEKU(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["core"]
	tests := []struct {
		name       string
		mutate     func(*x509.Certificate)
		expectCode string
	}{
		{
			name: "wrong spiffe",
			mutate: func(leaf *x509.Certificate) {
				leaf.URIs = []*url.URL{mustURL(t, serviceContracts["ai"].SPIFFEID)}
			},
			expectCode: "identity_invalid",
		},
		{
			name: "multiple spiffe",
			mutate: func(leaf *x509.Certificate) {
				leaf.URIs = append(
					leaf.URIs,
					mustURL(t, "spiffe://meetingmind.internal/ns/nonprod-v2/sa/extra"),
				)
			},
			expectCode: "identity_invalid",
		},
		{
			name: "wrong dns",
			mutate: func(leaf *x509.Certificate) {
				leaf.DNSNames = []string{"ai.meetingmind.internal"}
			},
			expectCode: "identity_invalid",
		},
		{
			name: "wildcard dns",
			mutate: func(leaf *x509.Certificate) {
				leaf.DNSNames = []string{"*.meetingmind.internal"}
			},
			expectCode: "identity_invalid",
		},
		{
			name: "ip san",
			mutate: func(leaf *x509.Certificate) {
				leaf.IPAddresses = []net.IP{net.ParseIP("127.0.0.1")}
			},
			expectCode: "identity_invalid",
		},
		{
			name: "email san",
			mutate: func(leaf *x509.Certificate) {
				leaf.EmailAddresses = []string{"core@meetingmind.internal"}
			},
			expectCode: "identity_invalid",
		},
		{
			name: "wrong eku",
			mutate: func(leaf *x509.Certificate) {
				leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}
			},
			expectCode: "eku_invalid",
		},
		{
			name: "unknown eku",
			mutate: func(leaf *x509.Certificate) {
				leaf.UnknownExtKeyUsage = []asn1.ObjectIdentifier{{1, 2, 3, 4}}
			},
			expectCode: "eku_invalid",
		},
		{
			name: "expired",
			mutate: func(leaf *x509.Certificate) {
				leaf.NotBefore = now.Add(-48 * time.Hour)
				leaf.NotAfter = now.Add(-time.Hour)
			},
			expectCode: "certificate_expired",
		},
		{
			name: "not yet valid",
			mutate: func(leaf *x509.Certificate) {
				leaf.NotBefore = now.Add(time.Hour)
				leaf.NotAfter = now.Add(89 * 24 * time.Hour)
			},
			expectCode: "certificate_not_yet_valid",
		},
		{
			name: "validity over 90 days",
			mutate: func(leaf *x509.Certificate) {
				leaf.NotBefore = now.Add(-time.Hour)
				leaf.NotAfter = leaf.NotBefore.Add(91 * 24 * time.Hour)
			},
			expectCode: "certificate_invalid",
		},
	}

	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			payload := testBundle(t, serviceContract, now, test.mutate)
			_, _, err := ValidateBundle(payload, serviceContract, now)
			if got := CodeOf(err); got != test.expectCode {
				t.Fatalf("CodeOf(error) = %q, want %q; error = %v", got, test.expectCode, err)
			}
		})
	}
}

func TestValidateBundleRejectsKeyMismatch(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["bff"]
	bundle := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
	otherKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	bundle.PrivateKeyPEM = encodePrivateKey(t, otherKey)
	payload := encodeTestBundle(t, bundle)

	_, _, err = ValidateBundle(payload, serviceContract, now)
	if got := CodeOf(err); got != "certificate_invalid" {
		t.Fatalf("CodeOf(error) = %q, want certificate_invalid", got)
	}
}

func TestValidateBundleRejectsUntrustedAndInconsistentChains(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["auth"]
	original := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
	other := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))

	originalIntermediate, _ := splitTwoPEMCertificates(t, original.CABundlePEM)
	otherIntermediate, otherRoot := splitTwoPEMCertificates(t, other.CABundlePEM)
	tests := []struct {
		name       string
		caBundle   string
		expectCode string
	}{
		{
			name:       "untrusted root",
			caBundle:   encodePEMBlocks(originalIntermediate, otherRoot),
			expectCode: "chain_invalid",
		},
		{
			name:       "different intermediate",
			caBundle:   encodePEMBlocks(otherIntermediate, otherRoot),
			expectCode: "certificate_invalid",
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			bundle := original
			bundle.CABundlePEM = test.caBundle
			_, _, err := ValidateBundle(
				encodeTestBundle(t, bundle),
				serviceContract,
				now,
			)
			if got := CodeOf(err); got != test.expectCode {
				t.Fatalf("CodeOf(error) = %q, want %q", got, test.expectCode)
			}
		})
	}
}

func TestValidateBundleAcceptsCAOverlapTrust(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["core"]
	original := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
	other := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
	originalIntermediate, originalRoot := splitTwoPEMCertificates(t, original.CABundlePEM)
	otherIntermediate, otherRoot := splitTwoPEMCertificates(t, other.CABundlePEM)

	tests := []struct {
		name     string
		caBundle string
	}{
		{
			name: "issuing pair first",
			caBundle: encodePEMBlocks(
				originalIntermediate, originalRoot, otherIntermediate, otherRoot,
			),
		},
		{
			name: "issuing pair second",
			caBundle: encodePEMBlocks(
				otherIntermediate, otherRoot, originalIntermediate, originalRoot,
			),
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			bundle := original
			bundle.CABundlePEM = test.caBundle
			material, metadata, err := ValidateBundle(
				encodeTestBundle(t, bundle),
				serviceContract,
				now,
			)
			if err != nil {
				t.Fatalf("ValidateBundle() error = %v", err)
			}
			if string(material.CABundle) != test.caBundle {
				t.Fatal("material CA bundle must keep the full overlap trust")
			}
			if metadata.Service != serviceContract.Service {
				t.Fatalf("metadata service = %q, want %q", metadata.Service, serviceContract.Service)
			}
		})
	}
}

func TestValidateBundleRejectsInvalidCAOverlapTrust(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["core"]
	original := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
	other := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
	third := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
	originalIntermediate, originalRoot := splitTwoPEMCertificates(t, original.CABundlePEM)
	otherIntermediate, otherRoot := splitTwoPEMCertificates(t, other.CABundlePEM)
	thirdIntermediate, thirdRoot := splitTwoPEMCertificates(t, third.CABundlePEM)
	expiredIntermediate, expiredRoot := testExpiredCAPair(t, now)

	tests := []struct {
		name       string
		caBundle   string
		expectCode string
	}{
		{
			name: "duplicate pair",
			caBundle: encodePEMBlocks(
				originalIntermediate, originalRoot, originalIntermediate, originalRoot,
			),
			expectCode: "ca_bundle_invalid",
		},
		{
			name: "duplicate root across pairs",
			caBundle: encodePEMBlocks(
				originalIntermediate, originalRoot, otherIntermediate, originalRoot,
			),
			expectCode: "ca_bundle_invalid",
		},
		{
			name: "odd certificate count",
			caBundle: encodePEMBlocks(
				originalIntermediate, originalRoot, otherIntermediate,
			),
			expectCode: "ca_bundle_invalid",
		},
		{
			name: "issuing pair missing",
			caBundle: encodePEMBlocks(
				otherIntermediate, otherRoot, thirdIntermediate, thirdRoot,
			),
			expectCode: "certificate_invalid",
		},
		{
			name: "expired second pair",
			caBundle: encodePEMBlocks(
				originalIntermediate, originalRoot, expiredIntermediate, expiredRoot,
			),
			expectCode: "ca_bundle_invalid",
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			bundle := original
			bundle.CABundlePEM = test.caBundle
			_, _, err := ValidateBundle(
				encodeTestBundle(t, bundle),
				serviceContract,
				now,
			)
			if got := CodeOf(err); got != test.expectCode {
				t.Fatalf("CodeOf(error) = %q, want %q", got, test.expectCode)
			}
		})
	}
}

func TestValidateBundleRejectsMalformedAndAmbiguousJSON(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["auth"]
	valid := testBundle(t, serviceContract, now, nil)
	var object map[string]any
	if err := json.Unmarshal(valid, &object); err != nil {
		t.Fatal(err)
	}
	object["unexpected"] = "value"
	unknownField, err := json.Marshal(object)
	if err != nil {
		t.Fatal(err)
	}

	tests := []struct {
		name    string
		payload []byte
	}{
		{name: "malformed", payload: []byte(`{"schemaVersion":`)},
		{name: "unknown field", payload: unknownField},
		{name: "trailing value", payload: append(append([]byte(nil), valid...), []byte(` {}`)...)},
		{name: "empty", payload: nil},
		{name: "oversized", payload: make([]byte, maxSecretBytes+1)},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			_, _, err := ValidateBundle(test.payload, serviceContract, now)
			if got := CodeOf(err); got != "bundle_invalid" {
				t.Fatalf("CodeOf(error) = %q, want bundle_invalid", got)
			}
		})
	}
}

func TestValidateBundleRejectsMetadataAndPEMChanges(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["stt"]
	tests := []struct {
		name       string
		mutate     func(*tlsBundle)
		expectCode string
	}{
		{
			name: "wrong environment",
			mutate: func(bundle *tlsBundle) {
				bundle.Environment = "production"
			},
			expectCode: "bundle_invalid",
		},
		{
			name: "wrong service",
			mutate: func(bundle *tlsBundle) {
				bundle.Service = "ai"
			},
			expectCode: "bundle_invalid",
		},
		{
			name: "wrong metadata time",
			mutate: func(bundle *tlsBundle) {
				bundle.NotAfter = now.Format(time.RFC3339)
			},
			expectCode: "bundle_invalid",
		},
		{
			name: "malformed certificate pem",
			mutate: func(bundle *tlsBundle) {
				bundle.CertificatePEM = "not a certificate"
			},
			expectCode: "certificate_invalid",
		},
		{
			name: "encrypted or legacy key pem",
			mutate: func(bundle *tlsBundle) {
				bundle.PrivateKeyPEM = strings.Replace(
					bundle.PrivateKeyPEM,
					"PRIVATE KEY",
					"EC PRIVATE KEY",
					2,
				)
			},
			expectCode: "private_key_invalid",
		},
		{
			name: "missing root",
			mutate: func(bundle *tlsBundle) {
				block, _ := pem.Decode([]byte(bundle.CABundlePEM))
				bundle.CABundlePEM = string(pem.EncodeToMemory(block))
			},
			expectCode: "ca_bundle_invalid",
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			bundle := decodeTestBundle(t, testBundle(t, serviceContract, now, nil))
			test.mutate(&bundle)
			_, _, err := ValidateBundle(
				encodeTestBundle(t, bundle),
				serviceContract,
				now,
			)
			if got := CodeOf(err); got != test.expectCode {
				t.Fatalf("CodeOf(error) = %q, want %q", got, test.expectCode)
			}
		})
	}
}

func testBundle(
	t *testing.T,
	serviceContract ServiceContract,
	now time.Time,
	mutateLeaf func(*x509.Certificate),
) []byte {
	t.Helper()
	rootKey := generateKey(t)
	rootTemplate := &x509.Certificate{
		SerialNumber:          big.NewInt(1),
		Subject:               pkix.Name{CommonName: "MeetingMind NonProd V2 Root CA"},
		NotBefore:             now.Add(-24 * time.Hour),
		NotAfter:              now.AddDate(5, 0, 0),
		IsCA:                  true,
		BasicConstraintsValid: true,
		MaxPathLen:            1,
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign,
		SignatureAlgorithm:    x509.ECDSAWithSHA256,
	}
	root := createCertificate(t, rootTemplate, rootTemplate, &rootKey.PublicKey, rootKey)

	intermediateKey := generateKey(t)
	intermediateTemplate := &x509.Certificate{
		SerialNumber:          big.NewInt(2),
		Subject:               pkix.Name{CommonName: "MeetingMind NonProd V2 Intermediate CA"},
		NotBefore:             now.Add(-24 * time.Hour),
		NotAfter:              now.AddDate(1, 0, 0),
		IsCA:                  true,
		BasicConstraintsValid: true,
		MaxPathLen:            0,
		MaxPathLenZero:        true,
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign,
		SignatureAlgorithm:    x509.ECDSAWithSHA256,
	}
	intermediate := createCertificate(
		t,
		intermediateTemplate,
		root,
		&intermediateKey.PublicKey,
		rootKey,
	)

	leafKey := generateKey(t)
	leafTemplate := &x509.Certificate{
		SerialNumber:          big.NewInt(3),
		Subject:               pkix.Name{CommonName: serviceContract.ServiceAccount},
		NotBefore:             now.Add(-time.Hour),
		NotAfter:              now.Add(89 * 24 * time.Hour),
		BasicConstraintsValid: true,
		KeyUsage:              x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           testExtKeyUsages(t, serviceContract.EKUs),
		DNSNames:              append([]string(nil), serviceContract.DNSSANs...),
		URIs:                  []*url.URL{mustURL(t, serviceContract.SPIFFEID)},
		SignatureAlgorithm:    x509.ECDSAWithSHA256,
	}
	if mutateLeaf != nil {
		mutateLeaf(leafTemplate)
	}
	leaf := createCertificate(
		t,
		leafTemplate,
		intermediate,
		&leafKey.PublicKey,
		intermediateKey,
	)
	bundle := tlsBundle{
		SchemaVersion:  1,
		Environment:    Environment,
		Service:        serviceContract.Service,
		SPIFFEID:       serviceContract.SPIFFEID,
		CertificatePEM: encodeCertificates(leaf, intermediate),
		PrivateKeyPEM:  encodePrivateKey(t, leafKey),
		CABundlePEM:    encodeCertificates(intermediate, root),
		NotBefore:      leaf.NotBefore.UTC().Format(time.RFC3339),
		NotAfter:       leaf.NotAfter.UTC().Format(time.RFC3339),
	}
	return encodeTestBundle(t, bundle)
}

func testExpiredCAPair(t *testing.T, now time.Time) (*pem.Block, *pem.Block) {
	t.Helper()
	rootKey := generateKey(t)
	rootTemplate := &x509.Certificate{
		SerialNumber:          big.NewInt(10),
		Subject:               pkix.Name{CommonName: "MeetingMind NonProd V2 Expired Root CA"},
		NotBefore:             now.AddDate(-2, 0, 0),
		NotAfter:              now.AddDate(-1, 0, 0),
		IsCA:                  true,
		BasicConstraintsValid: true,
		MaxPathLen:            1,
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign,
		SignatureAlgorithm:    x509.ECDSAWithSHA256,
	}
	root := createCertificate(t, rootTemplate, rootTemplate, &rootKey.PublicKey, rootKey)

	intermediateKey := generateKey(t)
	intermediateTemplate := &x509.Certificate{
		SerialNumber:          big.NewInt(11),
		Subject:               pkix.Name{CommonName: "MeetingMind NonProd V2 Expired Intermediate CA"},
		NotBefore:             now.AddDate(-2, 0, 0),
		NotAfter:              now.AddDate(-1, 0, 0),
		IsCA:                  true,
		BasicConstraintsValid: true,
		MaxPathLen:            0,
		MaxPathLenZero:        true,
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign,
		SignatureAlgorithm:    x509.ECDSAWithSHA256,
	}
	intermediate := createCertificate(
		t,
		intermediateTemplate,
		root,
		&intermediateKey.PublicKey,
		rootKey,
	)
	return &pem.Block{Type: "CERTIFICATE", Bytes: intermediate.Raw},
		&pem.Block{Type: "CERTIFICATE", Bytes: root.Raw}
}

func generateKey(t *testing.T) *ecdsa.PrivateKey {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	return key
}

func createCertificate(
	t *testing.T,
	template, parent *x509.Certificate,
	publicKey any,
	parentKey any,
) *x509.Certificate {
	t.Helper()
	der, err := x509.CreateCertificate(rand.Reader, template, parent, publicKey, parentKey)
	if err != nil {
		t.Fatal(err)
	}
	certificate, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatal(err)
	}
	return certificate
}

func encodeCertificates(certificates ...*x509.Certificate) string {
	var result strings.Builder
	for _, certificate := range certificates {
		result.Write(pem.EncodeToMemory(&pem.Block{
			Type:  "CERTIFICATE",
			Bytes: certificate.Raw,
		}))
	}
	return result.String()
}

func splitTwoPEMCertificates(t *testing.T, value string) (*pem.Block, *pem.Block) {
	t.Helper()
	first, rest := pem.Decode([]byte(value))
	second, rest := pem.Decode(rest)
	if first == nil || second == nil || len(strings.TrimSpace(string(rest))) != 0 {
		t.Fatal("expected exactly two PEM certificates")
	}
	return first, second
}

func encodePEMBlocks(blocks ...*pem.Block) string {
	var result strings.Builder
	for _, block := range blocks {
		result.Write(pem.EncodeToMemory(block))
	}
	return result.String()
}

func encodePrivateKey(t *testing.T, key *ecdsa.PrivateKey) string {
	t.Helper()
	der, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	return string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der}))
}

func mustURL(t *testing.T, value string) *url.URL {
	t.Helper()
	parsed, err := url.Parse(value)
	if err != nil {
		t.Fatal(err)
	}
	return parsed
}

func testExtKeyUsages(t *testing.T, values []string) []x509.ExtKeyUsage {
	t.Helper()
	result := make([]x509.ExtKeyUsage, 0, len(values))
	for _, value := range values {
		switch value {
		case "clientAuth":
			result = append(result, x509.ExtKeyUsageClientAuth)
		case "serverAuth":
			result = append(result, x509.ExtKeyUsageServerAuth)
		default:
			t.Fatalf("unexpected test EKU %q", value)
		}
	}
	return result
}

func decodeTestBundle(t *testing.T, payload []byte) tlsBundle {
	t.Helper()
	var bundle tlsBundle
	if err := json.Unmarshal(payload, &bundle); err != nil {
		t.Fatal(err)
	}
	return bundle
}

func encodeTestBundle(t *testing.T, bundle tlsBundle) []byte {
	t.Helper()
	payload, err := json.Marshal(bundle)
	if err != nil {
		t.Fatal(err)
	}
	return payload
}
