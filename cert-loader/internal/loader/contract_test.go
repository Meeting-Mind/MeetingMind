package loader

import (
	"testing"
)

func TestConfigValidateAcceptsExactContract(t *testing.T) {
	t.Parallel()
	for service, serviceContract := range serviceContracts {
		service := service
		serviceContract := serviceContract
		t.Run(service, func(t *testing.T) {
			t.Parallel()
			config := validTestConfig(serviceContract)
			got, err := config.Validate()
			if err != nil {
				t.Fatalf("Validate() error = %v", err)
			}
			if got.Service != service {
				t.Fatalf("Validate() service = %q, want %q", got.Service, service)
			}
		})
	}
}

func TestConfigValidateRejectsOutOfContractInputs(t *testing.T) {
	t.Parallel()
	base := validTestConfig(serviceContracts["auth"])
	tests := []struct {
		name   string
		mutate func(*Config)
	}{
		{name: "unknown service", mutate: func(config *Config) {
			config.ExpectedService = "unknown"
		}},
		{name: "wrong spiffe", mutate: func(config *Config) {
			config.ExpectedSPIFFE = serviceContracts["ai"].SPIFFEID
		}},
		{name: "wrong dns", mutate: func(config *Config) {
			config.ExpectedDNS = []string{"ai.meetingmind.internal"}
		}},
		{name: "wrong eku", mutate: func(config *Config) {
			config.ExpectedEKUs = []string{"clientAuth"}
		}},
		{name: "wrong output", mutate: func(config *Config) {
			config.OutputDir = "/tmp/tls"
		}},
		{name: "unknown version stage", mutate: func(config *Config) {
			config.VersionStage = "LATEST"
		}},
		{name: "wrong partition", mutate: func(config *Config) {
			config.SecretARN = "arn:aws-cn:secretsmanager:ap-northeast-2:123456789012:secret:/meetingmind-nonprod-v2/auth/tls-bundle-Ab12Cd"
		}},
		{name: "wrong region", mutate: func(config *Config) {
			config.SecretARN = "arn:aws:secretsmanager:us-east-1:123456789012:secret:/meetingmind-nonprod-v2/auth/tls-bundle-Ab12Cd"
		}},
		{name: "wrong account", mutate: func(config *Config) {
			config.SecretARN = "arn:aws:secretsmanager:ap-northeast-2:123:secret:/meetingmind-nonprod-v2/auth/tls-bundle-Ab12Cd"
		}},
		{name: "wrong service secret", mutate: func(config *Config) {
			config.SecretARN = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:/meetingmind-nonprod-v2/ai/tls-bundle-Ab12Cd"
		}},
		{name: "missing generated suffix", mutate: func(config *Config) {
			config.SecretARN = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:/meetingmind-nonprod-v2/auth/tls-bundle"
		}},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			config := base
			config.ExpectedDNS = append([]string(nil), base.ExpectedDNS...)
			config.ExpectedEKUs = append([]string(nil), base.ExpectedEKUs...)
			test.mutate(&config)
			if _, err := config.Validate(); CodeOf(err) != "config_invalid" {
				t.Fatalf("Validate() error = %v, want config_invalid", err)
			}
		})
	}
}

func validTestConfig(serviceContract ServiceContract) Config {
	return Config{
		SecretARN: "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:" +
			"/meetingmind-nonprod-v2/" + serviceContract.Service +
			"/tls-bundle-Ab12Cd",
		VersionStage:    "AWSCURRENT",
		ExpectedService: serviceContract.Service,
		ExpectedSPIFFE:  serviceContract.SPIFFEID,
		ExpectedDNS:     append([]string(nil), serviceContract.DNSSANs...),
		ExpectedEKUs:    append([]string(nil), serviceContract.EKUs...),
		OutputDir:       OutputDirectory,
	}
}
