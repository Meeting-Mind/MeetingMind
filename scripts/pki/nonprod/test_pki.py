#!/usr/bin/env python3
"""Integration tests that create only one-time PKI material in a temporary directory."""

from __future__ import annotations

import json
import os
import secrets
import stat
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pki


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
MANIFEST_DIRECTORY = SCRIPT_DIRECTORY / "manifests"


class NonProdPkiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temporary = tempfile.TemporaryDirectory(prefix="meetingmind-pki-test-")
        cls.base = Path(cls.temporary.name).resolve()
        os.chmod(cls.base, 0o700)
        cls.root_passphrase = cls.base / "root.passphrase"
        cls.intermediate_passphrase = cls.base / "intermediate.passphrase"
        cls.root_passphrase.write_text(secrets.token_urlsafe(48) + "\n", encoding="utf-8")
        cls.intermediate_passphrase.write_text(
            secrets.token_urlsafe(48) + "\n", encoding="utf-8"
        )
        os.chmod(cls.root_passphrase, 0o600)
        os.chmod(cls.intermediate_passphrase, 0o600)
        cls.ca_directory = cls.base / "ca"
        pki.initialize_ca(
            cls.ca_directory,
            cls.root_passphrase,
            cls.intermediate_passphrase,
        )

        cls.manifests: dict[str, dict[str, object]] = {}
        cls.outputs: dict[str, Path] = {}
        for manifest_path in sorted(MANIFEST_DIRECTORY.glob("*.json")):
            manifest = pki.load_manifest(manifest_path)
            service = str(manifest["service"])
            output = cls.base / f"issued-{service}"
            pki.issue_service_certificate(
                cls.ca_directory,
                manifest_path,
                output,
                cls.intermediate_passphrase,
            )
            cls.manifests[service] = manifest
            cls.outputs[service] = output

    @classmethod
    def tearDownClass(cls) -> None:
        cls.temporary.cleanup()

    def _issue_invalid(
        self,
        name: str,
        service: str = "core",
        **overrides: object,
    ) -> Path:
        output = self.base / name
        pki._issue_leaf_material(
            self.ca_directory,
            self.intermediate_passphrase,
            output,
            self.manifests[service],
            **overrides,
        )
        return output

    def _verify(self, service: str, output: Path, **kwargs: object) -> pki.CertificateMetadata:
        return pki.verify_service_certificate(
            self.manifests[service],
            output / "certificate.pem",
            output / "private-key.pem",
            output / "ca-bundle.pem",
            **kwargs,
        )

    def _validity(self, certificate: Path) -> tuple[datetime, datetime]:
        dates = pki._run(
            ["openssl", "x509", "-in", str(certificate), "-noout", "-dates"],
            "test certificate validity inspection",
        ).decode("ascii")
        parsed = {
            name: pki._parse_certificate_time(value)
            for name, value in (
                line.split("=", 1) for line in dates.splitlines() if "=" in line
            )
        }
        return parsed["notBefore"], parsed["notAfter"]

    def test_ca_lifetimes_and_encrypted_private_keys(self) -> None:
        root_certificate = self.ca_directory / "root/certs/root-ca.crt.pem"
        intermediate_certificate = (
            self.ca_directory / "intermediate/certs/intermediate-ca.crt.pem"
        )
        root_start, root_end = self._validity(root_certificate)
        intermediate_start, intermediate_end = self._validity(intermediate_certificate)
        self.assertEqual(timedelta(days=pki.ROOT_DAYS), root_end - root_start)
        self.assertEqual(
            timedelta(days=pki.INTERMEDIATE_DAYS),
            intermediate_end - intermediate_start,
        )
        for key in (
            self.ca_directory / "root/private/root-ca.key.pem",
            self.ca_directory / "intermediate/private/intermediate-ca.key.pem",
        ):
            with self.assertRaises(pki.PkiError):
                pki._run(
                    [
                        "openssl",
                        "pkey",
                        "-in",
                        str(key),
                        "-passin",
                        "pass:definitely-not-the-generated-passphrase",
                        "-noout",
                    ],
                    "encrypted CA key check",
                )

    def test_five_service_certificates_and_bundles(self) -> None:
        self.assertEqual({"bff", "auth", "core", "ai", "stt"}, set(self.outputs))
        for service, output in self.outputs.items():
            metadata = self._verify(service, output)
            self.assertFalse(metadata.rotation_required)
            self.assertLessEqual(
                metadata.not_after - metadata.not_before,
                timedelta(days=pki.LEAF_DAYS, minutes=5),
            )
            bundle_path = self.base / f"{service}-bundle.json"
            pki.create_bundle(
                self.manifests[service],
                output / "certificate.pem",
                output / "private-key.pem",
                output / "ca-bundle.pem",
                bundle_path,
            )
            self.assertEqual(0o600, stat.S_IMODE(bundle_path.stat().st_mode))
            bundle = json.loads(bundle_path.read_text(encoding="utf-8"))
            self.assertEqual(1, bundle["schemaVersion"])
            self.assertEqual(service, bundle["service"])
            self.assertEqual(self.manifests[service]["spiffeId"], bundle["spiffeId"])

    def test_wrong_and_multiple_spiffe_uri_are_rejected(self) -> None:
        wrong = self._issue_invalid(
            "wrong-spiffe",
            spiffe_ids=[
                "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-auth"
            ],
        )
        with self.assertRaisesRegex(pki.PkiError, "exactly the approved SPIFFE"):
            self._verify("core", wrong)

        multiple = self._issue_invalid(
            "multiple-spiffe",
            spiffe_ids=[
                str(self.manifests["core"]["spiffeId"]),
                "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-bff",
            ],
        )
        with self.assertRaisesRegex(pki.PkiError, "exactly the approved SPIFFE"):
            self._verify("core", multiple)

    def test_wrong_dns_wildcard_ip_and_eku_are_rejected(self) -> None:
        wrong_dns = self._issue_invalid(
            "wrong-dns",
            dns_sans=["wrong.meetingmind.internal"],
        )
        with self.assertRaisesRegex(pki.PkiError, "DNS SANs"):
            self._verify("core", wrong_dns)

        wildcard = self._issue_invalid(
            "wildcard-dns",
            dns_sans=["*.meetingmind.internal"],
        )
        with self.assertRaisesRegex(pki.PkiError, "DNS SANs"):
            self._verify("core", wildcard)

        wrong_eku = self._issue_invalid(
            "wrong-eku",
            extended_key_usages=["serverAuth"],
        )
        with self.assertRaisesRegex(pki.PkiError, "EKUs"):
            self._verify("core", wrong_eku)

        ip_san = self._issue_invalid(
            "ip-san",
            dns_sans=["core.meetingmind.internal"],
            spiffe_ids=[str(self.manifests["core"]["spiffeId"])],
            additional_sans=["IP:127.0.0.1"],
        )
        with self.assertRaisesRegex(pki.PkiError, "IP or unapproved SAN"):
            self._verify("core", ip_san)

    def test_certificate_private_key_mismatch_is_rejected(self) -> None:
        with self.assertRaisesRegex(pki.PkiError, "do not match"):
            pki.verify_service_certificate(
                self.manifests["core"],
                self.outputs["core"] / "certificate.pem",
                self.outputs["bff"] / "private-key.pem",
                self.outputs["core"] / "ca-bundle.pem",
            )

    def test_cli_emits_only_non_secret_metadata(self) -> None:
        command = [
            sys.executable,
            str(SCRIPT_DIRECTORY / "pki.py"),
            "verify",
            "--manifest",
            str(MANIFEST_DIRECTORY / "core.json"),
            "--certificate",
            str(self.outputs["core"] / "certificate.pem"),
            "--private-key",
            str(self.outputs["core"] / "private-key.pem"),
            "--ca-bundle",
            str(self.outputs["core"] / "ca-bundle.pem"),
        ]
        success = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
        )
        self.assertEqual(0, success.returncode)
        metadata = json.loads(success.stdout)
        self.assertEqual("core", metadata["service"])
        self.assertNotIn("BEGIN CERTIFICATE", success.stdout + success.stderr)
        self.assertNotIn("PRIVATE KEY", success.stdout + success.stderr)

        mismatch_command = command.copy()
        mismatch_command[mismatch_command.index("--private-key") + 1] = str(
            self.outputs["bff"] / "private-key.pem"
        )
        failure = subprocess.run(
            mismatch_command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
        )
        self.assertEqual(1, failure.returncode)
        self.assertEqual("", failure.stdout)
        self.assertIn("do not match", failure.stderr)
        self.assertNotIn("BEGIN CERTIFICATE", failure.stderr)
        self.assertNotIn("PRIVATE KEY-----", failure.stderr)

    def test_expired_and_not_yet_valid_certificates_are_rejected(self) -> None:
        now = datetime.now(timezone.utc).replace(microsecond=0)
        expired = self._issue_invalid(
            "expired",
            not_before=now - timedelta(days=2),
            not_after=now - timedelta(days=1),
        )
        with self.assertRaisesRegex(pki.PkiError, "expired"):
            self._verify("core", expired, now=now)

        future = self._issue_invalid(
            "not-yet-valid",
            not_before=now + timedelta(days=1),
            not_after=now + timedelta(days=2),
        )
        with self.assertRaisesRegex(pki.PkiError, "not yet valid"):
            self._verify("core", future, now=now)

    def test_rotation_threshold_is_thirty_days(self) -> None:
        now = datetime.now(timezone.utc).replace(microsecond=0)
        rotation = self._issue_invalid(
            "rotation",
            not_before=now - timedelta(days=1),
            not_after=now + timedelta(days=29),
        )
        self.assertTrue(self._verify("core", rotation, now=now).rotation_required)

        overlong = self._issue_invalid(
            "overlong",
            not_before=now - timedelta(days=1),
            not_after=now + timedelta(days=91),
        )
        with self.assertRaisesRegex(pki.PkiError, "exceeds 90 days"):
            self._verify("core", overlong, now=now)

    def test_repository_symlink_and_unsafe_permissions_are_rejected(self) -> None:
        with self.assertRaisesRegex(pki.PkiError, "outside the repository"):
            pki.validate_new_output_path(
                SCRIPT_DIRECTORY / "forbidden-output",
                "test output",
            )

        symlink = self.base / "output-link"
        symlink.symlink_to(self.base / "link-target")
        with self.assertRaisesRegex(pki.PkiError, "symlinks"):
            pki.validate_new_output_path(symlink / "child", "test output")

        unsafe_parent = self.base / "unsafe-parent"
        unsafe_parent.mkdir(mode=0o755)
        os.chmod(unsafe_parent, 0o755)
        with self.assertRaisesRegex(pki.PkiError, "group or other"):
            pki.validate_new_output_path(
                unsafe_parent / "certificate-output",
                "test output",
            )

        unsafe_private_file = self.base / "unsafe-private-input"
        unsafe_private_file.write_text(secrets.token_urlsafe(32), encoding="utf-8")
        os.chmod(unsafe_private_file, 0o644)
        with self.assertRaisesRegex(pki.PkiError, "group or other"):
            pki._validate_material_file(
                unsafe_private_file,
                "test private input",
                private=True,
            )

    def test_ca_overlap_trust_pairs(self) -> None:
        overlap_base = self.base / "overlap"
        overlap_base.mkdir()
        os.chmod(overlap_base, 0o700)
        passphrases: dict[str, Path] = {}
        for name in ("b-root", "b-intermediate", "c-root", "c-intermediate"):
            passphrase = overlap_base / f"{name}.passphrase"
            passphrase.write_text(secrets.token_urlsafe(48) + "\n", encoding="utf-8")
            os.chmod(passphrase, 0o600)
            passphrases[name] = passphrase

        manifest_path = MANIFEST_DIRECTORY / "core.json"
        second_ca = overlap_base / "ca-b"
        pki.initialize_ca(
            second_ca, passphrases["b-root"], passphrases["b-intermediate"]
        )
        second_output = overlap_base / "issued-core-b"
        pki.issue_service_certificate(
            second_ca, manifest_path, second_output, passphrases["b-intermediate"]
        )
        third_ca = overlap_base / "ca-c"
        pki.initialize_ca(
            third_ca, passphrases["c-root"], passphrases["c-intermediate"]
        )
        third_output = overlap_base / "issued-core-c"
        pki.issue_service_certificate(
            third_ca, manifest_path, third_output, passphrases["c-intermediate"]
        )

        def _write_trust(name: str, content: bytes) -> Path:
            path = overlap_base / name
            path.write_bytes(content)
            os.chmod(path, 0o600)
            return path

        trust_a = (self.outputs["core"] / "ca-bundle.pem").read_bytes()
        trust_b = (second_output / "ca-bundle.pem").read_bytes()
        trust_c = (third_output / "ca-bundle.pem").read_bytes()
        overlap_bundle = _write_trust("overlap-ca-bundle.pem", trust_a + trust_b)

        for output in (self.outputs["core"], second_output):
            pki.verify_service_certificate(
                self.manifests["core"],
                output / "certificate.pem",
                output / "private-key.pem",
                overlap_bundle,
            )

        bundle_json = overlap_base / "core-overlap-bundle.json"
        pki.create_bundle(
            self.manifests["core"],
            second_output / "certificate.pem",
            second_output / "private-key.pem",
            overlap_bundle,
            bundle_json,
        )
        document = json.loads(bundle_json.read_text(encoding="utf-8"))
        self.assertEqual(4, document["caBundlePem"].count("BEGIN CERTIFICATE"))

        rejected_bundles = {
            "duplicate-pair.pem": (
                trust_a + trust_a,
                "must not repeat",
            ),
            "odd-count.pem": (
                trust_a + pki._pem_certificates(second_output / "ca-bundle.pem")[0],
                "one or two intermediate and root",
            ),
            "missing-issuer.pem": (
                trust_b + trust_c,
                "does not match the CA bundle",
            ),
        }
        for name, (content, message) in rejected_bundles.items():
            bundle_path = _write_trust(name, content)
            with self.subTest(name=name):
                with self.assertRaisesRegex(pki.PkiError, message):
                    pki.verify_service_certificate(
                        self.manifests["core"],
                        self.outputs["core"] / "certificate.pem",
                        self.outputs["core"] / "private-key.pem",
                        bundle_path,
                    )

    def test_manifest_contract_rejects_changes(self) -> None:
        original = json.loads((MANIFEST_DIRECTORY / "core.json").read_text(encoding="utf-8"))
        invalid_directory = self.base / "invalid-manifests"
        invalid_directory.mkdir(mode=0o700)
        cases = {
            "wrong-spiffe": {
                **original,
                "spiffeId": "spiffe://meetingmind.internal/ns/nonprod-v2/sa/other",
            },
            "multiple-spiffe": {
                **original,
                "spiffeId": [
                    original["spiffeId"],
                    "spiffe://meetingmind.internal/ns/nonprod-v2/sa/other",
                ],
            },
            "wrong-dns": {**original, "dnsSans": ["wrong.meetingmind.internal"]},
            "wrong-eku": {**original, "extendedKeyUsages": ["serverAuth"]},
        }
        for name, document in cases.items():
            path = invalid_directory / f"{name}.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            with self.subTest(name=name):
                with self.assertRaisesRegex(pki.PkiError, "identity contract"):
                    pki.load_manifest(path)


if __name__ == "__main__":
    unittest.main(verbosity=2)
