package loader

import (
	"errors"
	"os"
	"path/filepath"
	"testing"
)

func TestAtomicWriterWritesExpectedFilesAndModes(t *testing.T) {
	t.Parallel()
	outputDirectory := t.TempDir()
	material := Material{
		PrivateKey:  []byte("private-key"),
		Certificate: []byte("certificate"),
		CABundle:    []byte("ca-bundle"),
	}
	if err := WriteMaterial(
		outputDirectory,
		material,
		os.Getuid(),
		os.Getgid(),
	); err != nil {
		t.Fatalf("WriteMaterial() error = %v", err)
	}
	expected := map[string]struct {
		content string
		mode    os.FileMode
	}{
		"tls.key": {content: "private-key", mode: 0o400},
		"tls.crt": {content: "certificate", mode: 0o444},
		"ca.crt":  {content: "ca-bundle", mode: 0o444},
	}
	for name, want := range expected {
		path := filepath.Join(outputDirectory, name)
		content, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		if string(content) != want.content {
			t.Fatalf("%s content = %q", name, content)
		}
		info, err := os.Stat(path)
		if err != nil {
			t.Fatal(err)
		}
		if info.Mode().Perm() != want.mode {
			t.Fatalf("%s mode = %#o, want %#o", name, info.Mode().Perm(), want.mode)
		}
	}
}

func TestAtomicWriterRejectsSymlinkOutput(t *testing.T) {
	t.Parallel()
	parent := t.TempDir()
	realDirectory := filepath.Join(parent, "real")
	if err := os.Mkdir(realDirectory, 0o700); err != nil {
		t.Fatal(err)
	}
	symlink := filepath.Join(parent, "link")
	if err := os.Symlink(realDirectory, symlink); err != nil {
		t.Fatal(err)
	}
	err := WriteMaterial(
		symlink,
		Material{PrivateKey: []byte("key"), Certificate: []byte("cert"), CABundle: []byte("ca")},
		os.Getuid(),
		os.Getgid(),
	)
	if got := CodeOf(err); got != "output_path_invalid" {
		t.Fatalf("CodeOf(error) = %q, want output_path_invalid", got)
	}
}

func TestAtomicWriterRejectsNonEmptyOutput(t *testing.T) {
	t.Parallel()
	outputDirectory := t.TempDir()
	if err := os.Symlink("/tmp/target", filepath.Join(outputDirectory, "tls.key")); err != nil {
		t.Fatal(err)
	}
	err := WriteMaterial(
		outputDirectory,
		Material{PrivateKey: []byte("key"), Certificate: []byte("cert"), CABundle: []byte("ca")},
		os.Getuid(),
		os.Getgid(),
	)
	if got := CodeOf(err); got != "output_not_empty" {
		t.Fatalf("CodeOf(error) = %q, want output_not_empty", got)
	}
}

func TestAtomicWriterRejectsGroupOrOtherWritableOutput(t *testing.T) {
	t.Parallel()
	outputDirectory := t.TempDir()
	if err := os.Chmod(outputDirectory, 0o777); err != nil {
		t.Fatal(err)
	}
	err := WriteMaterial(
		outputDirectory,
		Material{PrivateKey: []byte("key"), Certificate: []byte("cert"), CABundle: []byte("ca")},
		os.Getuid(),
		os.Getgid(),
	)
	if got := CodeOf(err); got != "output_path_invalid" {
		t.Fatalf("CodeOf(error) = %q, want output_path_invalid", got)
	}
}

func TestAtomicWriterRemovesPartialOutputAfterRenameFailure(t *testing.T) {
	t.Parallel()
	outputDirectory := t.TempDir()
	writer := newAtomicWriter(os.Getuid(), os.Getgid())
	renameCount := 0
	writer.rename = func(root *os.Root, oldName, newName string) error {
		renameCount++
		if renameCount == 2 {
			return errors.New("injected rename failure")
		}
		return root.Rename(oldName, newName)
	}
	err := writer.write(outputDirectory, Material{
		PrivateKey:  []byte("private-key"),
		Certificate: []byte("certificate"),
		CABundle:    []byte("ca-bundle"),
	})
	if got := CodeOf(err); got != "output_write_failed" {
		t.Fatalf("CodeOf(error) = %q, want output_write_failed", got)
	}
	entries, readErr := os.ReadDir(outputDirectory)
	if readErr != nil {
		t.Fatal(readErr)
	}
	if len(entries) != 0 {
		t.Fatalf("partial output remains after failure: %#v", entries)
	}
}

func TestAtomicWriterRejectsEmptyMaterial(t *testing.T) {
	t.Parallel()
	err := WriteMaterial(
		t.TempDir(),
		Material{Certificate: []byte("cert"), CABundle: []byte("ca")},
		os.Getuid(),
		os.Getgid(),
	)
	if got := CodeOf(err); got != "output_write_failed" {
		t.Fatalf("CodeOf(error) = %q, want output_write_failed", got)
	}
}
