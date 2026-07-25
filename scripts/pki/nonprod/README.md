# MeetingMind NonProd V2 Offline PKI

이 디렉터리는 T047-B1의 발급 계약과 도구만 보관한다. 실제 NonProd CA, private key,
passphrase, 발급 인증서와 Secrets Manager bundle JSON은 저장소에 두지 않는다.
Production은 이 NonProd CA를 재사용하지 않는다.

## 의존성과 선택 근거

- Python 3 표준 라이브러리: manifest, 경로·권한, JSON bundle과 오류 redaction을 담당한다.
- 시스템 `openssl`: ECDSA P-256 key와 X.509 certificate를 생성하고 chain을 검증한다.

새 패키지는 추가하지 않았다. shell만 사용하는 대안은 JSON과 SAN/EKU 파싱, 경로별
symlink·권한 검사와 오류 redaction이 취약해질 수 있어 사용하지 않았다. Python X.509
라이브러리를 새로 추가하는 대안도 현재 범위에는 필요하지 않다.

## 안전 경계

- 모든 material 출력 경로는 absolute path로 명시해야 한다.
- 출력은 저장소 밖에 있어야 하고, 기존 경로 또는 symlink를 사용할 수 없다.
- 출력의 기존 parent directory와 CA directory는 group/other permission이 없는 `0700`
  경계여야 한다.
- passphrase 파일과 private key는 symlink가 아닌 regular file이며 group/other
  permission이 없어야 한다.
- OpenSSL stdout/stderr는 성공·실패 모두 외부로 전달하지 않는다. CLI는 certificate
  fingerprint, validity와 SPIFFE ID 같은 비민감 metadata만 출력한다.
- 발급은 임시 staging directory에서 완료한 뒤 최종 경로로 rename한다.
- bundle JSON은 secret 원문이므로 `0600`으로 원자 기록하며 AWS로 업로드하지 않는다.

운영자는 암호화된 외부 저장 위치를 mount한 뒤 그 안에 `0700` 작업 디렉터리를 먼저
만든다. 아래 `/secure/offline-pki`는 예시이며 실제 위치를 저장소 문서에 기록하지 않는다.
passphrase는 shell argument나 환경변수에 넣지 말고 권한이 제한된 파일로 준비한다.

```bash
install -d -m 0700 /secure/offline-pki/session
python3 scripts/pki/nonprod/pki.py init-ca \
  --output /secure/offline-pki/session/ca \
  --root-passphrase-file /secure/offline-pki/session/root.passphrase \
  --intermediate-passphrase-file /secure/offline-pki/session/intermediate.passphrase
```

Root는 5년, intermediate는 1년이며 두 CA key는 AES-256으로 암호화된다. 서비스
certificate는 90일이고 기본 ECDSA P-256이다.

```bash
python3 scripts/pki/nonprod/pki.py issue \
  --ca-dir /secure/offline-pki/session/ca \
  --manifest scripts/pki/nonprod/manifests/core.json \
  --output /secure/offline-pki/session/core \
  --intermediate-passphrase-file /secure/offline-pki/session/intermediate.passphrase

python3 scripts/pki/nonprod/pki.py verify \
  --manifest scripts/pki/nonprod/manifests/core.json \
  --certificate /secure/offline-pki/session/core/certificate.pem \
  --private-key /secure/offline-pki/session/core/private-key.pem \
  --ca-bundle /secure/offline-pki/session/core/ca-bundle.pem

python3 scripts/pki/nonprod/pki.py bundle \
  --manifest scripts/pki/nonprod/manifests/core.json \
  --certificate /secure/offline-pki/session/core/certificate.pem \
  --private-key /secure/offline-pki/session/core/private-key.pem \
  --ca-bundle /secure/offline-pki/session/core/ca-bundle.pem \
  --output /secure/offline-pki/session/core-tls-bundle.json
```

`certificate.pem`은 leaf와 intermediate chain, `ca-bundle.pem`은 intermediate와 root
certificate를 순서대로 포함한다. bundle JSON은 `schemaVersion`, environment, service,
SPIFFE ID, certificate/private key/CA PEM, RFC 3339 validity를 한 문서에 넣는다.

CA overlap rotation window에서는 `--ca-bundle`에 old와 new `(intermediate, root)`
쌍을 이어 붙인 4개 certificate 파일을 넘길 수 있다. 검증기는 각 쌍의 chain을
독립 확인하고 leaf가 정확히 한 쌍에 연결되는지 검사하며, 중복 쌍과 홀수 개
certificate는 거부한다. 절차는 `infra/aws/nonprod-v2/rotation-runbook.md`를
따른다.

## 검증

테스트는 OS 임시 디렉터리의 일회성 CA만 생성하고 종료 시 전체 material을 삭제한다.

```bash
python3 scripts/pki/nonprod/test_pki.py
```

검증기는 다음을 fail closed한다.

- chain 또는 certificate/private-key 불일치
- expired/not-yet-valid 또는 90일을 초과하는 leaf
- exact 하나가 아닌 SPIFFE URI SAN
- service manifest와 다른 DNS SAN/EKU
- wildcard DNS, IP SAN 또는 추가 SAN type
- ECDSA P-256이 아닌 leaf

만료까지 30일 이하이면 검증 결과의 `rotationRequired`가 `true`다. 이 도구는
`AWSPENDING` 생성·승격, Secrets Manager write, Terraform apply 또는 ECS deployment를
수행하지 않는다.
