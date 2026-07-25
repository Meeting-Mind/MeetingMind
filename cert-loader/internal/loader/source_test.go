package loader

import (
	"context"
	"errors"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
)

type fakeSecretsManager struct {
	output *secretsmanager.GetSecretValueOutput
	err    error
	input  *secretsmanager.GetSecretValueInput
}

func (fake *fakeSecretsManager) GetSecretValue(
	_ context.Context,
	input *secretsmanager.GetSecretValueInput,
	_ ...func(*secretsmanager.Options),
) (*secretsmanager.GetSecretValueOutput, error) {
	fake.input = input
	return fake.output, fake.err
}

func TestAWSSourceFetchUsesExactARNAndStage(t *testing.T) {
	t.Parallel()
	const secretARN = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:/meetingmind-nonprod-v2/auth/tls-bundle-Ab12Cd"
	const payload = `{"safe":"test"}`
	client := &fakeSecretsManager{output: &secretsmanager.GetSecretValueOutput{
		SecretString:  aws.String(payload),
		VersionId:     aws.String("version-id"),
		VersionStages: []string{"AWSCURRENT"},
	}}
	value, err := newAWSSource(client).Fetch(
		context.Background(),
		secretARN,
		"AWSCURRENT",
	)
	if err != nil {
		t.Fatalf("Fetch() error = %v", err)
	}
	if aws.ToString(client.input.SecretId) != secretARN ||
		aws.ToString(client.input.VersionStage) != "AWSCURRENT" {
		t.Fatalf("GetSecretValue input = %#v", client.input)
	}
	if string(value.Payload) != payload || value.VersionID != "version-id" {
		t.Fatalf("Fetch() = %#v", value)
	}
}

func TestAWSSourceFetchAcceptsBinary(t *testing.T) {
	t.Parallel()
	client := &fakeSecretsManager{output: &secretsmanager.GetSecretValueOutput{
		SecretBinary:  []byte("payload"),
		VersionId:     aws.String("version-id"),
		VersionStages: []string{"AWSPENDING"},
	}}
	value, err := newAWSSource(client).Fetch(
		context.Background(),
		"test-arn",
		"AWSPENDING",
	)
	if err != nil {
		t.Fatalf("Fetch() error = %v", err)
	}
	if string(value.Payload) != "payload" {
		t.Fatalf("Fetch() payload = %q", value.Payload)
	}
}

func TestAWSSourceFetchRejectsInvalidResponses(t *testing.T) {
	t.Parallel()
	tests := []struct {
		name   string
		client *fakeSecretsManager
		code   string
	}{
		{
			name:   "aws error",
			client: &fakeSecretsManager{err: errors.New("sensitive upstream detail")},
			code:   "secret_fetch_failed",
		},
		{
			name:   "nil response",
			client: &fakeSecretsManager{},
			code:   "secret_response_invalid",
		},
		{
			name: "missing version",
			client: &fakeSecretsManager{output: &secretsmanager.GetSecretValueOutput{
				SecretString:  aws.String("{}"),
				VersionStages: []string{"AWSCURRENT"},
			}},
			code: "secret_response_invalid",
		},
		{
			name: "wrong stage",
			client: &fakeSecretsManager{output: &secretsmanager.GetSecretValueOutput{
				SecretString:  aws.String("{}"),
				VersionId:     aws.String("version"),
				VersionStages: []string{"AWSPREVIOUS"},
			}},
			code: "secret_response_invalid",
		},
		{
			name: "both payload forms",
			client: &fakeSecretsManager{output: &secretsmanager.GetSecretValueOutput{
				SecretString:  aws.String("{}"),
				SecretBinary:  []byte("{}"),
				VersionId:     aws.String("version"),
				VersionStages: []string{"AWSCURRENT"},
			}},
			code: "secret_response_invalid",
		},
		{
			name: "missing payload",
			client: &fakeSecretsManager{output: &secretsmanager.GetSecretValueOutput{
				VersionId:     aws.String("version"),
				VersionStages: []string{"AWSCURRENT"},
			}},
			code: "secret_response_invalid",
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			_, err := newAWSSource(test.client).Fetch(
				context.Background(),
				"test-arn",
				"AWSCURRENT",
			)
			if got := CodeOf(err); got != test.code {
				t.Fatalf("CodeOf(error) = %q, want %q", got, test.code)
			}
			if err != nil && err.Error() != test.code {
				t.Fatalf("error leaked cause: %q", err)
			}
		})
	}
}
