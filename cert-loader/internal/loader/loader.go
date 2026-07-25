package loader

import (
	"context"
	"time"
)

type MaterialWriter interface {
	Write(string, Material, int, int) error
}

type defaultWriter struct{}

func (defaultWriter) Write(
	outputDirectory string,
	material Material,
	uid int,
	gid int,
) error {
	return WriteMaterial(outputDirectory, material, uid, gid)
}

func Load(
	ctx context.Context,
	config Config,
	source SecretSource,
	now time.Time,
) (Metadata, error) {
	return load(ctx, config, source, defaultWriter{}, now)
}

func load(
	ctx context.Context,
	config Config,
	source SecretSource,
	writer MaterialWriter,
	now time.Time,
) (Metadata, error) {
	contract, err := config.Validate()
	if err != nil {
		return Metadata{}, err
	}
	secret, err := source.Fetch(ctx, config.SecretARN, config.VersionStage)
	if err != nil {
		return Metadata{}, err
	}
	defer clear(secret.Payload)
	material, metadata, err := ValidateBundle(secret.Payload, contract, now)
	if err != nil {
		return Metadata{}, err
	}
	defer clear(material.PrivateKey)
	if err := writer.Write(
		config.OutputDir,
		material,
		ApplicationUID,
		ApplicationGID,
	); err != nil {
		return Metadata{}, err
	}
	metadata.VersionID = secret.VersionID
	return metadata, nil
}
