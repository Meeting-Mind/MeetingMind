package loader

import (
	"errors"
	"strings"
	"testing"
)

func TestLoaderErrorRedactsCause(t *testing.T) {
	t.Parallel()
	const secret = "sensitive-secret-payload"
	err := fail("certificate_invalid", errors.New(secret))
	if err.Error() != "certificate_invalid" {
		t.Fatalf("Error() = %q", err.Error())
	}
	if strings.Contains(err.Error(), secret) {
		t.Fatal("error text exposed the underlying secret")
	}
	if CodeOf(err) != "certificate_invalid" {
		t.Fatalf("CodeOf() = %q", CodeOf(err))
	}
	if CodeOf(errors.New(secret)) != "internal_failure" {
		t.Fatalf("CodeOf(non-loader error) = %q", CodeOf(errors.New(secret)))
	}
}
