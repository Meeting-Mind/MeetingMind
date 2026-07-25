package loader

import (
	"context"
	"testing"
	"time"
)

type fakeSource struct {
	value SecretValue
	err   error
}

func (source fakeSource) Fetch(
	context.Context,
	string,
	string,
) (SecretValue, error) {
	return source.value, source.err
}

type recordingWriter struct {
	outputDirectory string
	material        Material
	uid             int
	gid             int
	err             error
}

func (writer *recordingWriter) Write(
	outputDirectory string,
	material Material,
	uid int,
	gid int,
) error {
	writer.outputDirectory = outputDirectory
	writer.material = material
	writer.uid = uid
	writer.gid = gid
	return writer.err
}

func TestLoadValidatesBeforeWriting(t *testing.T) {
	t.Parallel()
	now := time.Date(2026, time.July, 25, 0, 0, 0, 0, time.UTC)
	serviceContract := serviceContracts["ai"]
	source := fakeSource{value: SecretValue{
		Payload:   testBundle(t, serviceContract, now, nil),
		VersionID: "version-id",
	}}
	writer := &recordingWriter{}
	metadata, err := load(
		context.Background(),
		validTestConfig(serviceContract),
		source,
		writer,
		now,
	)
	if err != nil {
		t.Fatalf("load() error = %v", err)
	}
	if writer.outputDirectory != OutputDirectory ||
		writer.uid != ApplicationUID ||
		writer.gid != ApplicationGID ||
		len(writer.material.PrivateKey) == 0 {
		t.Fatalf("writer call = %#v", writer)
	}
	if metadata.VersionID != "version-id" {
		t.Fatalf("metadata version = %q", metadata.VersionID)
	}
}

func TestLoadDoesNotFetchOrWriteWithInvalidConfig(t *testing.T) {
	t.Parallel()
	config := validTestConfig(serviceContracts["ai"])
	config.OutputDir = "/tmp/not-allowed"
	writer := &recordingWriter{}
	_, err := load(
		context.Background(),
		config,
		fakeSource{},
		writer,
		time.Now(),
	)
	if got := CodeOf(err); got != "config_invalid" {
		t.Fatalf("CodeOf(error) = %q, want config_invalid", got)
	}
	if writer.outputDirectory != "" {
		t.Fatal("writer was called for invalid config")
	}
}

func TestLoadDoesNotWriteInvalidBundle(t *testing.T) {
	t.Parallel()
	writer := &recordingWriter{}
	_, err := load(
		context.Background(),
		validTestConfig(serviceContracts["ai"]),
		fakeSource{value: SecretValue{Payload: []byte(`{"invalid":true}`)}},
		writer,
		time.Now(),
	)
	if got := CodeOf(err); got != "bundle_invalid" {
		t.Fatalf("CodeOf(error) = %q, want bundle_invalid", got)
	}
	if writer.outputDirectory != "" {
		t.Fatal("writer was called for invalid bundle")
	}
}
