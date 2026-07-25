package loader

import (
	"fmt"
	"path/filepath"
	"reflect"
	"regexp"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws/arn"
)

const (
	Environment      = "nonprod-v2"
	OutputDirectory  = "/run/meetingmind/tls"
	ApplicationUID   = 10001
	ApplicationGID   = 10001
	maxSecretBytes   = 1024 * 1024
	leafValidityDays = 90
	rotationDays     = 30
)

type ServiceContract struct {
	Service        string
	ServiceAccount string
	SPIFFEID       string
	DNSSANs        []string
	EKUs           []string
}

var serviceContracts = map[string]ServiceContract{
	"bff": contract("bff", "meetingmind-bff", nil, []string{"clientAuth"}),
	"auth": contract(
		"auth",
		"meetingmind-auth",
		[]string{"auth.meetingmind.internal"},
		[]string{"serverAuth"},
	),
	"core": contract(
		"core",
		"meetingmind-core",
		[]string{"core.meetingmind.internal"},
		[]string{"clientAuth", "serverAuth"},
	),
	"ai": contract(
		"ai",
		"meetingmind-ai",
		[]string{"ai.meetingmind.internal"},
		[]string{"serverAuth"},
	),
	"stt": contract(
		"stt",
		"meetingmind-realtime-stt",
		[]string{"stt.meetingmind.internal"},
		[]string{"serverAuth"},
	),
}

func contract(service, account string, dnsSANs, ekus []string) ServiceContract {
	return ServiceContract{
		Service:        service,
		ServiceAccount: account,
		SPIFFEID: fmt.Sprintf(
			"spiffe://meetingmind.internal/ns/nonprod-v2/sa/%s",
			account,
		),
		DNSSANs: dnsSANs,
		EKUs:    ekus,
	}
}

type Config struct {
	SecretARN       string
	VersionStage    string
	ExpectedService string
	ExpectedSPIFFE  string
	ExpectedDNS     []string
	ExpectedEKUs    []string
	OutputDir       string
}

func (config Config) Validate() (ServiceContract, error) {
	contract, found := serviceContracts[config.ExpectedService]
	if !found {
		return ServiceContract{}, fail("config_invalid", nil)
	}
	if config.ExpectedSPIFFE != contract.SPIFFEID ||
		!reflect.DeepEqual(config.ExpectedDNS, contract.DNSSANs) ||
		!reflect.DeepEqual(config.ExpectedEKUs, contract.EKUs) {
		return ServiceContract{}, fail("config_invalid", nil)
	}
	if config.OutputDir != OutputDirectory || !filepath.IsAbs(config.OutputDir) ||
		filepath.Clean(config.OutputDir) != config.OutputDir {
		return ServiceContract{}, fail("config_invalid", nil)
	}
	switch config.VersionStage {
	case "AWSCURRENT", "AWSPENDING", "AWSPREVIOUS":
	default:
		return ServiceContract{}, fail("config_invalid", nil)
	}
	parsedARN, err := arn.Parse(config.SecretARN)
	if err != nil ||
		parsedARN.Partition != "aws" ||
		parsedARN.Service != "secretsmanager" ||
		parsedARN.Region != "ap-northeast-2" ||
		!regexp.MustCompile(`^[0-9]{12}$`).MatchString(parsedARN.AccountID) {
		return ServiceContract{}, fail("config_invalid", err)
	}
	resourcePrefix := fmt.Sprintf(
		"secret:/meetingmind-nonprod-v2/%s/tls-bundle-",
		contract.Service,
	)
	if !strings.HasPrefix(parsedARN.Resource, resourcePrefix) ||
		!regexp.MustCompile(`^[A-Za-z0-9]{6}$`).
			MatchString(strings.TrimPrefix(parsedARN.Resource, resourcePrefix)) {
		return ServiceContract{}, fail("config_invalid", nil)
	}
	return contract, nil
}
