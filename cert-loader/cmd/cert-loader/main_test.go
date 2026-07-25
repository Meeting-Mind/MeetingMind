package main

import (
	"reflect"
	"testing"
)

func TestParseConfig(t *testing.T) {
	t.Parallel()
	arguments := []string{
		"--secret-arn", "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:/meetingmind-nonprod-v2/core/tls-bundle-Ab12Cd",
		"--version-stage", "AWSPENDING",
		"--expected-service", "core",
		"--expected-spiffe-id", "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-core",
		"--expected-dns-san", "core.meetingmind.internal",
		"--expected-eku", "clientAuth",
		"--expected-eku", "serverAuth",
		"--output-dir", "/run/meetingmind/tls",
	}
	config, err := parseConfig(arguments)
	if err != nil {
		t.Fatalf("parseConfig() error = %v", err)
	}
	if !reflect.DeepEqual(config.ExpectedDNS, []string{"core.meetingmind.internal"}) ||
		!reflect.DeepEqual(config.ExpectedEKUs, []string{"clientAuth", "serverAuth"}) {
		t.Fatalf("parseConfig() = %#v", config)
	}
	if _, err := config.Validate(); err != nil {
		t.Fatalf("config.Validate() error = %v", err)
	}
}

func TestParseConfigRejectsUnknownOrPositionalArguments(t *testing.T) {
	t.Parallel()
	tests := [][]string{
		{"--unknown", "value"},
		{"positional"},
	}
	for _, arguments := range tests {
		if _, err := parseConfig(arguments); err == nil {
			t.Fatalf("parseConfig(%q) unexpectedly succeeded", arguments)
		}
	}
}
