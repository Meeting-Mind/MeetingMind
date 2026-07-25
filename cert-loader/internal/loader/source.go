package loader

import (
	"context"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
)

type SecretValue struct {
	Payload   []byte
	VersionID string
}

type SecretSource interface {
	Fetch(context.Context, string, string) (SecretValue, error)
}

type getSecretValueAPI interface {
	GetSecretValue(
		context.Context,
		*secretsmanager.GetSecretValueInput,
		...func(*secretsmanager.Options),
	) (*secretsmanager.GetSecretValueOutput, error)
}

type AWSSource struct {
	client getSecretValueAPI
}

func NewAWSSource(ctx context.Context) (*AWSSource, error) {
	awsConfig, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		return nil, fail("aws_config_failed", err)
	}
	return &AWSSource{client: secretsmanager.NewFromConfig(awsConfig)}, nil
}

func newAWSSource(client getSecretValueAPI) *AWSSource {
	return &AWSSource{client: client}
}

func (source *AWSSource) Fetch(
	ctx context.Context,
	secretARN string,
	versionStage string,
) (SecretValue, error) {
	output, err := source.client.GetSecretValue(
		ctx,
		&secretsmanager.GetSecretValueInput{
			SecretId:     aws.String(secretARN),
			VersionStage: aws.String(versionStage),
		},
	)
	if err != nil {
		return SecretValue{}, fail("secret_fetch_failed", err)
	}
	if output == nil || output.VersionId == nil || *output.VersionId == "" ||
		!contains(output.VersionStages, versionStage) {
		return SecretValue{}, fail("secret_response_invalid", nil)
	}
	var payload []byte
	switch {
	case output.SecretString != nil && output.SecretBinary == nil:
		payload = []byte(*output.SecretString)
	case output.SecretString == nil && output.SecretBinary != nil:
		payload = append([]byte(nil), output.SecretBinary...)
	default:
		return SecretValue{}, fail("secret_response_invalid", nil)
	}
	return SecretValue{
		Payload:   payload,
		VersionID: *output.VersionId,
	}, nil
}

func contains(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}
