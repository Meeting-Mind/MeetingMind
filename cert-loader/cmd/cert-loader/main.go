package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"os"
	"time"

	"github.com/Meeting-Mind/MeetingMind/cert-loader/internal/loader"
)

const awsTimeout = 15 * time.Second

type stringList []string

func (list *stringList) String() string {
	return ""
}

func (list *stringList) Set(value string) error {
	*list = append(*list, value)
	return nil
}

func parseConfig(arguments []string) (loader.Config, error) {
	flags := flag.NewFlagSet("meetingmind-cert-loader", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	var expectedDNS stringList
	var expectedEKUs stringList
	config := loader.Config{}
	flags.StringVar(&config.SecretARN, "secret-arn", "", "")
	flags.StringVar(&config.VersionStage, "version-stage", "", "")
	flags.StringVar(&config.ExpectedService, "expected-service", "", "")
	flags.StringVar(&config.ExpectedSPIFFE, "expected-spiffe-id", "", "")
	flags.Var(&expectedDNS, "expected-dns-san", "")
	flags.Var(&expectedEKUs, "expected-eku", "")
	flags.StringVar(&config.OutputDir, "output-dir", "", "")
	if err := flags.Parse(arguments); err != nil {
		return loader.Config{}, err
	}
	if flags.NArg() != 0 {
		return loader.Config{}, fmt.Errorf("unexpected positional arguments")
	}
	config.ExpectedDNS = expectedDNS
	config.ExpectedEKUs = expectedEKUs
	return config, nil
}

func run(arguments []string) int {
	config, err := parseConfig(arguments)
	if err != nil {
		fmt.Fprintln(os.Stderr, "cert_loader_failed code=config_invalid")
		return 1
	}
	if _, err := config.Validate(); err != nil {
		fmt.Fprintln(os.Stderr, "cert_loader_failed code=config_invalid")
		return 1
	}
	ctx, cancel := context.WithTimeout(context.Background(), awsTimeout)
	defer cancel()
	source, err := loader.NewAWSSource(ctx)
	if err != nil {
		fmt.Fprintf(
			os.Stderr,
			"cert_loader_failed code=%s\n",
			loader.CodeOf(err),
		)
		return 1
	}
	metadata, err := loader.Load(ctx, config, source, time.Now().UTC())
	if err != nil {
		fmt.Fprintf(
			os.Stderr,
			"cert_loader_failed code=%s\n",
			loader.CodeOf(err),
		)
		return 1
	}
	fmt.Printf(
		"certificate_loaded fingerprint_sha256=%s not_after=%s secret_version_id=%s\n",
		metadata.FingerprintSHA256,
		metadata.NotAfter.Format(time.RFC3339),
		metadata.VersionID,
	)
	return 0
}

func main() {
	os.Exit(run(os.Args[1:]))
}
