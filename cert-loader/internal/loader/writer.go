package loader

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"io/fs"
	"os"
	"path/filepath"
)

type atomicWriter struct {
	uid    int
	gid    int
	rename func(*os.Root, string, string) error
}

func newAtomicWriter(uid, gid int) *atomicWriter {
	return &atomicWriter{
		uid: uid,
		gid: gid,
		rename: func(root *os.Root, oldName, newName string) error {
			return root.Rename(oldName, newName)
		},
	}
}

func WriteMaterial(outputDirectory string, material Material, uid, gid int) error {
	return newAtomicWriter(uid, gid).write(outputDirectory, material)
}

func (writer *atomicWriter) write(outputDirectory string, material Material) error {
	if !filepath.IsAbs(outputDirectory) ||
		filepath.Clean(outputDirectory) != outputDirectory {
		return fail("output_path_invalid", nil)
	}
	info, err := os.Lstat(outputDirectory)
	if err != nil ||
		!info.IsDir() ||
		info.Mode()&os.ModeSymlink != 0 ||
		info.Mode().Perm()&0o022 != 0 {
		return fail("output_path_invalid", err)
	}
	entries, err := os.ReadDir(outputDirectory)
	if err != nil || len(entries) != 0 {
		return fail("output_not_empty", err)
	}
	root, err := os.OpenRoot(outputDirectory)
	if err != nil {
		return fail("output_path_invalid", err)
	}
	defer root.Close()

	stagingName, err := randomStagingName()
	if err != nil {
		return fail("output_write_failed", err)
	}
	if err := root.Mkdir(stagingName, 0o700); err != nil {
		return fail("output_write_failed", err)
	}
	cleanupStaging := true
	defer func() {
		if cleanupStaging {
			_ = root.RemoveAll(stagingName)
		}
	}()

	files := []struct {
		name    string
		content []byte
		mode    fs.FileMode
	}{
		{name: "tls.key", content: material.PrivateKey, mode: 0o400},
		{name: "tls.crt", content: material.Certificate, mode: 0o444},
		{name: "ca.crt", content: material.CABundle, mode: 0o444},
	}
	for _, file := range files {
		if len(file.content) == 0 {
			return fail("output_write_failed", nil)
		}
		if err := writer.writeFile(
			root,
			filepath.Join(stagingName, file.name),
			file.content,
			file.mode,
		); err != nil {
			return err
		}
	}
	if err := syncRootDirectory(root, stagingName); err != nil {
		return fail("output_write_failed", err)
	}

	created := make([]string, 0, len(files))
	for _, file := range files {
		if _, err := root.Lstat(file.name); !errors.Is(err, fs.ErrNotExist) {
			writer.removeCreated(root, created)
			return fail("output_target_exists", err)
		}
		if err := writer.rename(
			root,
			filepath.Join(stagingName, file.name),
			file.name,
		); err != nil {
			writer.removeCreated(root, created)
			return fail("output_write_failed", err)
		}
		created = append(created, file.name)
	}
	if err := syncRootDirectory(root, "."); err != nil {
		writer.removeCreated(root, created)
		return fail("output_write_failed", err)
	}
	if err := root.Remove(stagingName); err != nil {
		writer.removeCreated(root, created)
		return fail("output_write_failed", err)
	}
	cleanupStaging = false
	return nil
}

func (writer *atomicWriter) writeFile(
	root *os.Root,
	name string,
	content []byte,
	mode fs.FileMode,
) error {
	file, err := root.OpenFile(name, os.O_WRONLY|os.O_CREATE|os.O_EXCL, mode)
	if err != nil {
		return fail("output_write_failed", err)
	}
	written, err := file.Write(content)
	if err != nil || written != len(content) {
		file.Close()
		return fail("output_write_failed", err)
	}
	if err := file.Sync(); err != nil {
		file.Close()
		return fail("output_write_failed", err)
	}
	if err := file.Chmod(mode); err != nil {
		file.Close()
		return fail("output_write_failed", err)
	}
	if err := file.Chown(writer.uid, writer.gid); err != nil {
		file.Close()
		return fail("output_write_failed", err)
	}
	if err := file.Close(); err != nil {
		return fail("output_write_failed", err)
	}
	return nil
}

func (writer *atomicWriter) removeCreated(root *os.Root, names []string) {
	for _, name := range names {
		_ = root.Remove(name)
	}
}

func syncRootDirectory(root *os.Root, name string) error {
	directory, err := root.Open(name)
	if err != nil {
		return err
	}
	defer directory.Close()
	return directory.Sync()
}

func randomStagingName() (string, error) {
	random := make([]byte, 16)
	if _, err := rand.Read(random); err != nil {
		return "", err
	}
	return ".staging-" + hex.EncodeToString(random), nil
}
