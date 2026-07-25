#!/usr/bin/env python3
"""Fail-closed offline PKI tooling for MeetingMind NonProd V2."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterable


ENVIRONMENT = "nonprod-v2"
TRUST_DOMAIN = "meetingmind.internal"
SPIFFE_PREFIX = f"spiffe://{TRUST_DOMAIN}/ns/{ENVIRONMENT}/sa/"
ROOT_DAYS = 1825
INTERMEDIATE_DAYS = 365
LEAF_DAYS = 90
ROTATION_DAYS = 30
REPOSITORY_ROOT = Path(__file__).resolve().parents[3]

SERVICE_CONTRACTS = {
    "bff": {
        "serviceAccount": "meetingmind-bff",
        "dnsSans": [],
        "extendedKeyUsages": ["clientAuth"],
    },
    "auth": {
        "serviceAccount": "meetingmind-auth",
        "dnsSans": ["auth.meetingmind.internal"],
        "extendedKeyUsages": ["serverAuth"],
    },
    "core": {
        "serviceAccount": "meetingmind-core",
        "dnsSans": ["core.meetingmind.internal"],
        "extendedKeyUsages": ["clientAuth", "serverAuth"],
    },
    "ai": {
        "serviceAccount": "meetingmind-ai",
        "dnsSans": ["ai.meetingmind.internal"],
        "extendedKeyUsages": ["serverAuth"],
    },
    "stt": {
        "serviceAccount": "meetingmind-realtime-stt",
        "dnsSans": ["stt.meetingmind.internal"],
        "extendedKeyUsages": ["serverAuth"],
    },
}

MANIFEST_FIELDS = {
    "schemaVersion",
    "environment",
    "service",
    "serviceAccount",
    "spiffeId",
    "dnsSans",
    "extendedKeyUsages",
}
CERTIFICATE_PATTERN = re.compile(
    rb"-----BEGIN CERTIFICATE-----\s+.+?-----END CERTIFICATE-----\s*",
    re.DOTALL,
)


class PkiError(RuntimeError):
    """Expected fail-closed validation error."""


@dataclass(frozen=True)
class CertificateMetadata:
    not_before: datetime
    not_after: datetime
    fingerprint_sha256: str
    rotation_required: bool


def _run(arguments: list[str], operation: str, *, input_bytes: bytes | None = None) -> bytes:
    try:
        result = subprocess.run(
            arguments,
            input=input_bytes,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise PkiError(f"{operation} could not start") from exc
    if result.returncode != 0:
        raise PkiError(f"{operation} failed")
    return result.stdout


def _is_within(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def _require_absolute(path: Path, label: str) -> None:
    if not path.is_absolute():
        raise PkiError(f"{label} must be an absolute path")
    if any(char in str(path) for char in ("\n", "\r", "\x00", '"')):
        raise PkiError(f"{label} contains an unsupported character")


def _reject_repository_path(path: Path, label: str) -> None:
    resolved = path.resolve(strict=False)
    if _is_within(resolved, REPOSITORY_ROOT):
        raise PkiError(f"{label} must be outside the repository")


def _reject_symlink_components(path: Path, label: str) -> None:
    current = Path(path.anchor)
    for part in path.parts[1:]:
        current = current / part
        if current.exists() or current.is_symlink():
            if stat.S_ISLNK(current.lstat().st_mode):
                raise PkiError(f"{label} must not contain symlinks")


def _require_private_directory(path: Path, label: str) -> None:
    if not path.is_dir():
        raise PkiError(f"{label} must be a directory")
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise PkiError(f"{label} must not grant group or other permissions")


def validate_new_output_path(path: Path, label: str) -> Path:
    _require_absolute(path, label)
    _reject_repository_path(path, label)
    _reject_symlink_components(path, label)
    if path.exists() or path.is_symlink():
        raise PkiError(f"{label} must not already exist")

    parent = path.parent
    if not parent.exists():
        raise PkiError(f"{label} parent directory must already exist")
    _require_private_directory(parent, f"{label} parent directory")
    return path


def _validate_material_directory(path: Path, label: str) -> Path:
    _require_absolute(path, label)
    _reject_repository_path(path, label)
    _reject_symlink_components(path, label)
    _require_private_directory(path, label)
    return path


def _validate_material_file(
    path: Path,
    label: str,
    *,
    private: bool,
) -> Path:
    _require_absolute(path, label)
    _reject_repository_path(path, label)
    _reject_symlink_components(path, label)
    if not path.is_file():
        raise PkiError(f"{label} must be a regular file")
    if private and stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise PkiError(f"{label} must not grant group or other permissions")
    return path


def _atomic_write(path: Path, content: bytes, mode: int) -> None:
    temporary = path.with_name(f".{path.name}.{secrets.token_hex(8)}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    except BaseException:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
        raise


def _write_text(path: Path, content: str, mode: int = 0o600) -> None:
    _atomic_write(path, content.encode("utf-8"), mode)


def _openssl_config_path(path: Path) -> str:
    return str(path)


def _root_config(root_directory: Path) -> str:
    root = _openssl_config_path(root_directory)
    return f"""\
[ ca ]
default_ca = CA_default

[ CA_default ]
dir = {root}
database = $dir/index.txt
new_certs_dir = $dir/newcerts
certificate = $dir/certs/root-ca.crt.pem
private_key = $dir/private/root-ca.key.pem
serial = $dir/serial
default_md = sha256
default_days = {INTERMEDIATE_DAYS}
policy = policy_strict
unique_subject = no
copy_extensions = none

[ policy_strict ]
commonName = supplied

[ req ]
prompt = no
distinguished_name = req_dn
x509_extensions = v3_root_ca

[ req_dn ]
commonName = MeetingMind NonProd V2 Root CA

[ v3_root_ca ]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
basicConstraints = critical, CA:true, pathlen:1
keyUsage = critical, keyCertSign, cRLSign

[ v3_intermediate_ca ]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
"""


def _intermediate_config(intermediate_directory: Path) -> str:
    intermediate = _openssl_config_path(intermediate_directory)
    return f"""\
[ ca ]
default_ca = CA_default

[ CA_default ]
dir = {intermediate}
database = $dir/index.txt
new_certs_dir = $dir/newcerts
certificate = $dir/certs/intermediate-ca.crt.pem
private_key = $dir/private/intermediate-ca.key.pem
serial = $dir/serial
default_md = sha256
default_days = {LEAF_DAYS}
policy = policy_leaf
unique_subject = no
copy_extensions = none

[ policy_leaf ]
commonName = supplied
"""


def _prepare_ca_directory(directory: Path) -> None:
    directory.mkdir(mode=0o700)
    for child in ("certs", "csr", "newcerts", "private"):
        (directory / child).mkdir(mode=0o700)
    _write_text(directory / "index.txt", "")
    _write_text(directory / "serial", f"{secrets.randbits(128):032X}\n")


def _generate_encrypted_key(path: Path, passphrase_file: Path) -> None:
    _run(
        [
            "openssl",
            "genpkey",
            "-algorithm",
            "EC",
            "-pkeyopt",
            "ec_paramgen_curve:prime256v1",
            "-pkeyopt",
            "ec_param_enc:named_curve",
            "-aes-256-cbc",
            "-pass",
            f"file:{passphrase_file}",
            "-out",
            str(path),
        ],
        "encrypted EC private-key generation",
    )
    os.chmod(path, 0o600)


def _generate_leaf_key(path: Path) -> None:
    _run(
        [
            "openssl",
            "genpkey",
            "-algorithm",
            "EC",
            "-pkeyopt",
            "ec_paramgen_curve:prime256v1",
            "-pkeyopt",
            "ec_param_enc:named_curve",
            "-out",
            str(path),
        ],
        "leaf EC private-key generation",
    )
    os.chmod(path, 0o600)


def initialize_ca(
    output_directory: Path,
    root_passphrase_file: Path,
    intermediate_passphrase_file: Path,
) -> None:
    output_directory = validate_new_output_path(output_directory, "CA output directory")
    root_passphrase_file = _validate_material_file(
        root_passphrase_file,
        "root passphrase file",
        private=True,
    )
    intermediate_passphrase_file = _validate_material_file(
        intermediate_passphrase_file,
        "intermediate passphrase file",
        private=True,
    )

    staging = Path(tempfile.mkdtemp(prefix=".meetingmind-ca-", dir=output_directory.parent))
    os.chmod(staging, 0o700)
    try:
        root_directory = staging / "root"
        intermediate_directory = staging / "intermediate"
        _prepare_ca_directory(root_directory)
        _prepare_ca_directory(intermediate_directory)
        root_config = root_directory / "openssl.cnf"
        intermediate_config = intermediate_directory / "openssl.cnf"
        _write_text(root_config, _root_config(root_directory))
        _write_text(intermediate_config, _intermediate_config(intermediate_directory))

        root_key = root_directory / "private/root-ca.key.pem"
        root_certificate = root_directory / "certs/root-ca.crt.pem"
        _generate_encrypted_key(root_key, root_passphrase_file)
        _run(
            [
                "openssl",
                "req",
                "-new",
                "-x509",
                "-config",
                str(root_config),
                "-extensions",
                "v3_root_ca",
                "-key",
                str(root_key),
                "-passin",
                f"file:{root_passphrase_file}",
                "-sha256",
                "-days",
                str(ROOT_DAYS),
                "-out",
                str(root_certificate),
            ],
            "root CA certificate creation",
        )
        os.chmod(root_certificate, 0o644)

        intermediate_key = intermediate_directory / "private/intermediate-ca.key.pem"
        intermediate_csr = intermediate_directory / "csr/intermediate-ca.csr.pem"
        intermediate_certificate = intermediate_directory / "certs/intermediate-ca.crt.pem"
        _generate_encrypted_key(intermediate_key, intermediate_passphrase_file)
        _run(
            [
                "openssl",
                "req",
                "-new",
                "-sha256",
                "-key",
                str(intermediate_key),
                "-passin",
                f"file:{intermediate_passphrase_file}",
                "-subj",
                "/CN=MeetingMind NonProd V2 Intermediate CA",
                "-out",
                str(intermediate_csr),
            ],
            "intermediate CA request creation",
        )
        _run(
            [
                "openssl",
                "ca",
                "-batch",
                "-notext",
                "-config",
                str(root_config),
                "-extensions",
                "v3_intermediate_ca",
                "-days",
                str(INTERMEDIATE_DAYS),
                "-keyfile",
                str(root_key),
                "-passin",
                f"file:{root_passphrase_file}",
                "-in",
                str(intermediate_csr),
                "-out",
                str(intermediate_certificate),
            ],
            "intermediate CA certificate signing",
        )
        os.chmod(intermediate_certificate, 0o644)
        _run(
            [
                "openssl",
                "verify",
                "-CAfile",
                str(root_certificate),
                str(intermediate_certificate),
            ],
            "intermediate CA chain verification",
        )
        _atomic_write(
            staging / "ca-bundle.pem",
            intermediate_certificate.read_bytes() + root_certificate.read_bytes(),
            0o644,
        )
        _write_text(
            root_config,
            _root_config(output_directory / "root"),
        )
        _write_text(
            intermediate_config,
            _intermediate_config(output_directory / "intermediate"),
        )
        os.replace(staging, output_directory)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise


def load_manifest(path: Path) -> dict[str, object]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise PkiError("service manifest is not valid JSON") from exc
    if not isinstance(document, dict) or set(document) != MANIFEST_FIELDS:
        raise PkiError("service manifest fields do not match the contract")
    service = document.get("service")
    if not isinstance(service, str) or service not in SERVICE_CONTRACTS:
        raise PkiError("service manifest has an unknown service")
    contract = SERVICE_CONTRACTS[service]
    expected = {
        "schemaVersion": 1,
        "environment": ENVIRONMENT,
        "service": service,
        "serviceAccount": contract["serviceAccount"],
        "spiffeId": SPIFFE_PREFIX + str(contract["serviceAccount"]),
        "dnsSans": contract["dnsSans"],
        "extendedKeyUsages": contract["extendedKeyUsages"],
    }
    if document != expected:
        raise PkiError("service manifest does not exactly match the approved identity contract")
    return document


def _leaf_extensions(
    spiffe_ids: list[str],
    dns_sans: list[str],
    extended_key_usages: list[str],
    additional_sans: list[str] | None = None,
) -> str:
    san_values = [f"URI:{value}" for value in spiffe_ids]
    san_values.extend(f"DNS:{value}" for value in dns_sans)
    san_values.extend(additional_sans or [])
    return f"""\
[ leaf_cert ]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature
extendedKeyUsage = {", ".join(extended_key_usages)}
subjectAltName = {", ".join(san_values)}
"""


def _format_openssl_time(value: datetime) -> str:
    return value.astimezone(timezone.utc).strftime("%y%m%d%H%M%SZ")


def _issue_leaf_material(
    ca_directory: Path,
    intermediate_passphrase_file: Path,
    output_directory: Path,
    manifest: dict[str, object],
    *,
    spiffe_ids: list[str] | None = None,
    dns_sans: list[str] | None = None,
    extended_key_usages: list[str] | None = None,
    additional_sans: list[str] | None = None,
    not_before: datetime | None = None,
    not_after: datetime | None = None,
) -> None:
    output_directory = validate_new_output_path(output_directory, "certificate output directory")
    ca_directory = _validate_material_directory(ca_directory, "CA directory")
    intermediate_passphrase_file = _validate_material_file(
        intermediate_passphrase_file,
        "intermediate passphrase file",
        private=True,
    )
    root_certificate = ca_directory / "root/certs/root-ca.crt.pem"
    intermediate_directory = ca_directory / "intermediate"
    intermediate_certificate = intermediate_directory / "certs/intermediate-ca.crt.pem"
    intermediate_key = intermediate_directory / "private/intermediate-ca.key.pem"
    intermediate_config = intermediate_directory / "openssl.cnf"
    for path, label, private in (
        (root_certificate, "root CA certificate", False),
        (intermediate_certificate, "intermediate CA certificate", False),
        (intermediate_key, "intermediate CA private key", True),
        (intermediate_config, "intermediate CA configuration", True),
    ):
        _validate_material_file(path, label, private=private)

    staging = Path(tempfile.mkdtemp(prefix=".meetingmind-leaf-", dir=output_directory.parent))
    os.chmod(staging, 0o700)
    try:
        key = staging / "private-key.pem"
        csr = staging / "request.csr.pem"
        leaf = staging / "leaf.crt.pem"
        extensions = staging / "leaf-extensions.cnf"
        _generate_leaf_key(key)
        _run(
            [
                "openssl",
                "req",
                "-new",
                "-sha256",
                "-key",
                str(key),
                "-subj",
                f"/CN={manifest['serviceAccount']}",
                "-out",
                str(csr),
            ],
            "leaf certificate request creation",
        )
        _write_text(
            extensions,
            _leaf_extensions(
                spiffe_ids or [str(manifest["spiffeId"])],
                dns_sans if dns_sans is not None else list(manifest["dnsSans"]),
                (
                    extended_key_usages
                    if extended_key_usages is not None
                    else list(manifest["extendedKeyUsages"])
                ),
                additional_sans,
            ),
        )
        sign_command = [
            "openssl",
            "ca",
            "-batch",
            "-notext",
            "-config",
            str(intermediate_config),
            "-extfile",
            str(extensions),
            "-extensions",
            "leaf_cert",
            "-keyfile",
            str(intermediate_key),
            "-passin",
            f"file:{intermediate_passphrase_file}",
            "-in",
            str(csr),
            "-out",
            str(leaf),
        ]
        if not_before is None and not_after is None:
            sign_command.extend(["-days", str(LEAF_DAYS)])
        elif not_before is not None and not_after is not None and not_before < not_after:
            sign_command.extend(
                [
                    "-startdate",
                    _format_openssl_time(not_before),
                    "-enddate",
                    _format_openssl_time(not_after),
                ]
            )
        else:
            raise PkiError("test validity override must contain an ordered start and end")
        _run(sign_command, "leaf certificate signing")
        os.chmod(leaf, 0o644)
        _atomic_write(
            staging / "certificate.pem",
            leaf.read_bytes() + intermediate_certificate.read_bytes(),
            0o644,
        )
        _atomic_write(
            staging / "ca-bundle.pem",
            intermediate_certificate.read_bytes() + root_certificate.read_bytes(),
            0o644,
        )
        extensions.unlink()
        os.replace(staging, output_directory)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise


def issue_service_certificate(
    ca_directory: Path,
    manifest_path: Path,
    output_directory: Path,
    intermediate_passphrase_file: Path,
) -> CertificateMetadata:
    manifest = load_manifest(manifest_path)
    _issue_leaf_material(
        ca_directory,
        intermediate_passphrase_file,
        output_directory,
        manifest,
    )
    return verify_service_certificate(
        manifest,
        output_directory / "certificate.pem",
        output_directory / "private-key.pem",
        output_directory / "ca-bundle.pem",
    )


def _pem_certificates(path: Path) -> list[bytes]:
    content = path.read_bytes()
    certificates = CERTIFICATE_PATTERN.findall(content)
    if not certificates or b"".join(certificates).strip() != content.strip():
        raise PkiError("certificate file contains invalid or non-certificate PEM data")
    return [certificate.strip() + b"\n" for certificate in certificates]


def _certificate_text(certificate_path: Path) -> str:
    return _run(
        ["openssl", "x509", "-in", str(certificate_path), "-noout", "-text"],
        "certificate inspection",
    ).decode("utf-8", errors="strict")


def _extension_lines(certificate_text: str, extension_name: str) -> list[str]:
    lines = certificate_text.splitlines()
    for index, line in enumerate(lines):
        if f"X509v3 {extension_name}:" in line:
            values: list[str] = []
            for following in lines[index + 1 :]:
                stripped = following.strip()
                if not stripped:
                    continue
                if stripped.startswith("X509v3 ") or not following.startswith(" " * 12):
                    break
                values.append(stripped)
            return values
    raise PkiError(f"certificate is missing {extension_name}")


def _parse_sans(certificate_text: str) -> tuple[list[str], list[str], list[str]]:
    values = ", ".join(_extension_lines(certificate_text, "Subject Alternative Name"))
    uris: list[str] = []
    dns_names: list[str] = []
    other: list[str] = []
    for item in (part.strip() for part in values.split(",")):
        if item.startswith("URI:"):
            uris.append(item.removeprefix("URI:"))
        elif item.startswith("DNS:"):
            dns_names.append(item.removeprefix("DNS:"))
        elif item:
            other.append(item)
    return uris, dns_names, other


def _parse_ekus(certificate_text: str) -> list[str]:
    values = ", ".join(_extension_lines(certificate_text, "Extended Key Usage"))
    names = {
        "TLS Web Client Authentication": "clientAuth",
        "TLS Web Server Authentication": "serverAuth",
    }
    parsed: list[str] = []
    for value in (part.strip() for part in values.split(",")):
        if value not in names:
            raise PkiError("certificate contains an unapproved extended key usage")
        parsed.append(names[value])
    return parsed


def _assert_leaf_constraints(certificate_text: str) -> None:
    basic_constraints = ", ".join(
        _extension_lines(certificate_text, "Basic Constraints")
    ).replace(" ", "")
    if basic_constraints != "CA:FALSE":
        raise PkiError("certificate basic constraints must be CA:false")
    key_usage = ", ".join(_extension_lines(certificate_text, "Key Usage"))
    if key_usage != "Digital Signature":
        raise PkiError("certificate key usage must be digitalSignature only")


def _parse_certificate_time(value: str) -> datetime:
    try:
        parsed = datetime.strptime(value.strip(), "%b %d %H:%M:%S %Y %Z")
    except ValueError as exc:
        raise PkiError("certificate validity could not be parsed") from exc
    return parsed.replace(tzinfo=timezone.utc)


def _certificate_metadata(certificate_path: Path, now: datetime) -> CertificateMetadata:
    dates = _run(
        ["openssl", "x509", "-in", str(certificate_path), "-noout", "-dates"],
        "certificate validity inspection",
    ).decode("ascii", errors="strict")
    parsed: dict[str, datetime] = {}
    for line in dates.splitlines():
        if "=" in line:
            name, value = line.split("=", 1)
            parsed[name] = _parse_certificate_time(value)
    if set(parsed) != {"notBefore", "notAfter"}:
        raise PkiError("certificate validity is incomplete")
    not_before = parsed["notBefore"]
    not_after = parsed["notAfter"]
    if now < not_before:
        raise PkiError("certificate is not yet valid")
    if now >= not_after:
        raise PkiError("certificate is expired")
    if not_after - not_before > timedelta(days=LEAF_DAYS, minutes=5):
        raise PkiError("leaf certificate validity exceeds 90 days")
    fingerprint = hashlib.sha256(
        _run(
            [
                "openssl",
                "x509",
                "-in",
                str(certificate_path),
                "-outform",
                "DER",
            ],
            "certificate fingerprint calculation",
        )
    ).hexdigest()
    return CertificateMetadata(
        not_before=not_before,
        not_after=not_after,
        fingerprint_sha256=fingerprint,
        rotation_required=not_after - now <= timedelta(days=ROTATION_DAYS),
    )


def _assert_p256(certificate_path: Path) -> None:
    public_key = _run(
        ["openssl", "x509", "-in", str(certificate_path), "-pubkey", "-noout"],
        "certificate public-key extraction",
    )
    description = _run(
        ["openssl", "pkey", "-pubin", "-text", "-noout"],
        "certificate public-key inspection",
        input_bytes=public_key,
    ).decode("utf-8", errors="strict")
    if "prime256v1" not in description and "P-256" not in description:
        raise PkiError("certificate public key is not ECDSA P-256")


def _assert_key_matches(certificate_path: Path, private_key_path: Path) -> None:
    certificate_public_key = _run(
        ["openssl", "x509", "-in", str(certificate_path), "-pubkey", "-noout"],
        "certificate public-key extraction",
    )
    private_public_key = _run(
        ["openssl", "pkey", "-in", str(private_key_path), "-pubout"],
        "private-key public-key extraction",
    )
    if certificate_public_key.strip() != private_public_key.strip():
        raise PkiError("certificate and private key do not match")


def _write_certificates_to_temporary_files(
    certificates: Iterable[bytes],
    directory: Path,
    prefix: str,
) -> list[Path]:
    paths: list[Path] = []
    for index, certificate in enumerate(certificates):
        path = directory / f"{prefix}-{index}.pem"
        _atomic_write(path, certificate, 0o600)
        paths.append(path)
    return paths


def verify_service_certificate(
    manifest: dict[str, object],
    certificate_path: Path,
    private_key_path: Path,
    ca_bundle_path: Path,
    *,
    now: datetime | None = None,
) -> CertificateMetadata:
    certificate_path = _validate_material_file(
        certificate_path, "certificate", private=False
    )
    private_key_path = _validate_material_file(
        private_key_path, "private key", private=True
    )
    ca_bundle_path = _validate_material_file(
        ca_bundle_path, "CA bundle", private=False
    )
    certificate_chain = _pem_certificates(certificate_path)
    ca_chain = _pem_certificates(ca_bundle_path)
    if len(certificate_chain) not in (1, 2):
        raise PkiError("certificate PEM must contain the leaf and optional intermediate")
    if len(ca_chain) not in (2, 4):
        raise PkiError(
            "CA bundle must contain one or two intermediate and root certificate pairs"
        )

    with tempfile.TemporaryDirectory(prefix="meetingmind-pki-verify-") as temporary_name:
        temporary = Path(temporary_name)
        os.chmod(temporary, 0o700)
        leaf, *presented_intermediate = _write_certificates_to_temporary_files(
            certificate_chain, temporary, "certificate"
        )
        ca_paths = _write_certificates_to_temporary_files(ca_chain, temporary, "ca")
        ca_pairs = [
            (ca_paths[index], ca_paths[index + 1])
            for index in range(0, len(ca_paths), 2)
        ]
        metadata = _certificate_metadata(
            leaf,
            now or datetime.now(timezone.utc),
        )
        pair_ders = [
            (
                _run(
                    ["openssl", "x509", "-in", str(pair_intermediate), "-outform", "DER"],
                    "CA bundle intermediate inspection",
                ),
                _run(
                    ["openssl", "x509", "-in", str(pair_root), "-outform", "DER"],
                    "CA bundle root inspection",
                ),
            )
            for pair_intermediate, pair_root in ca_pairs
        ]
        if len(ca_pairs) == 2 and (
            pair_ders[0][0] == pair_ders[1][0] or pair_ders[0][1] == pair_ders[1][1]
        ):
            raise PkiError(
                "CA bundle pairs must not repeat an intermediate or root certificate"
            )
        for pair_intermediate, pair_root in ca_pairs:
            _run(
                ["openssl", "verify", "-CAfile", str(pair_root), str(pair_root)],
                "root CA self-signature verification",
            )
            _run(
                ["openssl", "verify", "-CAfile", str(pair_root), str(pair_intermediate)],
                "intermediate CA chain verification",
            )

        def _leaf_chain_arguments(pair: tuple[Path, Path]) -> list[str]:
            pair_intermediate, pair_root = pair
            return [
                "openssl",
                "verify",
                "-CAfile",
                str(pair_root),
                "-untrusted",
                str(pair_intermediate),
                str(leaf),
            ]

        if presented_intermediate:
            presented = _run(
                ["openssl", "x509", "-in", str(presented_intermediate[0]), "-outform", "DER"],
                "presented intermediate inspection",
            )
            matching = [
                index
                for index, ders in enumerate(pair_ders)
                if ders[0] == presented
            ]
            if len(matching) != 1:
                raise PkiError("presented intermediate does not match the CA bundle")
            _run(
                _leaf_chain_arguments(ca_pairs[matching[0]]),
                "leaf certificate chain verification",
            )
        else:
            issuing_pairs = []
            for pair in ca_pairs:
                try:
                    _run(
                        _leaf_chain_arguments(pair),
                        "leaf certificate chain verification",
                    )
                except PkiError:
                    continue
                issuing_pairs.append(pair)
            if len(issuing_pairs) != 1:
                raise PkiError("leaf must chain to exactly one CA bundle pair")
        certificate_text = _certificate_text(leaf)
        _assert_leaf_constraints(certificate_text)
        uris, dns_names, other_sans = _parse_sans(certificate_text)
        expected_uri = str(manifest["spiffeId"])
        expected_dns = list(manifest["dnsSans"])
        if uris != [expected_uri]:
            raise PkiError("certificate must contain exactly the approved SPIFFE URI SAN")
        if other_sans:
            raise PkiError("certificate contains an IP or unapproved SAN type")
        if dns_names != expected_dns:
            raise PkiError("certificate DNS SANs do not exactly match the service contract")
        if any("*" in dns_name for dns_name in dns_names):
            raise PkiError("wildcard DNS SANs are not allowed")
        ekus = _parse_ekus(certificate_text)
        if ekus != list(manifest["extendedKeyUsages"]):
            raise PkiError("certificate EKUs do not exactly match the service contract")
        _assert_p256(leaf)
        _assert_key_matches(leaf, private_key_path)
        return metadata


def create_bundle(
    manifest: dict[str, object],
    certificate_path: Path,
    private_key_path: Path,
    ca_bundle_path: Path,
    output_path: Path,
) -> CertificateMetadata:
    output_path = validate_new_output_path(output_path, "bundle output file")
    if len(_pem_certificates(certificate_path)) != 2:
        raise PkiError("bundle certificate PEM must contain leaf and intermediate certificates")
    metadata = verify_service_certificate(
        manifest,
        certificate_path,
        private_key_path,
        ca_bundle_path,
    )
    bundle = {
        "schemaVersion": 1,
        "environment": ENVIRONMENT,
        "service": manifest["service"],
        "spiffeId": manifest["spiffeId"],
        "certificatePem": certificate_path.read_text(encoding="ascii"),
        "privateKeyPem": private_key_path.read_text(encoding="ascii"),
        "caBundlePem": ca_bundle_path.read_text(encoding="ascii"),
        "notBefore": metadata.not_before.isoformat().replace("+00:00", "Z"),
        "notAfter": metadata.not_after.isoformat().replace("+00:00", "Z"),
    }
    _atomic_write(
        output_path,
        (json.dumps(bundle, indent=2) + "\n").encode("utf-8"),
        0o600,
    )
    return metadata


def _metadata_document(manifest: dict[str, object], metadata: CertificateMetadata) -> str:
    return json.dumps(
        {
            "service": manifest["service"],
            "spiffeId": manifest["spiffeId"],
            "fingerprintSha256": metadata.fingerprint_sha256,
            "notBefore": metadata.not_before.isoformat().replace("+00:00", "Z"),
            "notAfter": metadata.not_after.isoformat().replace("+00:00", "Z"),
            "rotationRequired": metadata.rotation_required,
        },
        sort_keys=True,
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    initialize = subparsers.add_parser("init-ca", help="create an encrypted offline CA")
    initialize.add_argument("--output", type=Path, required=True)
    initialize.add_argument("--root-passphrase-file", type=Path, required=True)
    initialize.add_argument("--intermediate-passphrase-file", type=Path, required=True)

    issue = subparsers.add_parser("issue", help="issue one exact service certificate")
    issue.add_argument("--ca-dir", type=Path, required=True)
    issue.add_argument("--manifest", type=Path, required=True)
    issue.add_argument("--output", type=Path, required=True)
    issue.add_argument("--intermediate-passphrase-file", type=Path, required=True)

    verify = subparsers.add_parser("verify", help="verify an issued service certificate")
    verify.add_argument("--manifest", type=Path, required=True)
    verify.add_argument("--certificate", type=Path, required=True)
    verify.add_argument("--private-key", type=Path, required=True)
    verify.add_argument("--ca-bundle", type=Path, required=True)

    bundle = subparsers.add_parser(
        "bundle", help="create a Secrets Manager TLS bundle JSON file"
    )
    bundle.add_argument("--manifest", type=Path, required=True)
    bundle.add_argument("--certificate", type=Path, required=True)
    bundle.add_argument("--private-key", type=Path, required=True)
    bundle.add_argument("--ca-bundle", type=Path, required=True)
    bundle.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    try:
        if arguments.command == "init-ca":
            initialize_ca(
                arguments.output,
                arguments.root_passphrase_file,
                arguments.intermediate_passphrase_file,
            )
            print("offline CA initialized")
        elif arguments.command == "issue":
            manifest = load_manifest(arguments.manifest)
            metadata = issue_service_certificate(
                arguments.ca_dir,
                arguments.manifest,
                arguments.output,
                arguments.intermediate_passphrase_file,
            )
            print(_metadata_document(manifest, metadata))
        elif arguments.command == "verify":
            manifest = load_manifest(arguments.manifest)
            metadata = verify_service_certificate(
                manifest,
                arguments.certificate,
                arguments.private_key,
                arguments.ca_bundle,
            )
            print(_metadata_document(manifest, metadata))
        elif arguments.command == "bundle":
            manifest = load_manifest(arguments.manifest)
            metadata = create_bundle(
                manifest,
                arguments.certificate,
                arguments.private_key,
                arguments.ca_bundle,
                arguments.output,
            )
            print(_metadata_document(manifest, metadata))
        else:
            raise PkiError("unknown command")
        return 0
    except PkiError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
