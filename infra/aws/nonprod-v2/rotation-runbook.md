# NonProd V2 TLS Rotation Runbook

leaf 인증서 rotation, rollback과 offline CA overlap rotation의 고정 절차다.
`mtls-implementation-plan.md` 10장을 실행 명령 수준으로 구체화하며, 실제 실행은
Phase 8 이후 별도 사용자 승인과 offline CA 저장 위치 확인 뒤에만 수행한다.

## 1. 공통 원칙

- 인증서 PEM, private key, passphrase, secret 값은 명령 출력, shell 히스토리,
  로그, evidence 어디에도 남기지 않는다. bundle JSON은 repository 밖 사용자
  지정 경로에서만 다루고 secret 입력은 `file://` 참조로 수행한다.
- evidence는 UTC 시각, 서비스, secret version ID, leaf SHA-256 fingerprint,
  task definition revision, deployment 상태만 기록한다.
- leaf rotation은 만료 30일 전 시작한다. cert-loader가 성공 로그의
  `not_after`로 rotation window 진입을 보여준다.
- 이전 secret version과 task definition revision은 검증 window가 끝나기 전에
  삭제하지 않는다.
- 각 TLS bundle secret은 resource policy가 해당 서비스 task role 외의
  `GetSecretValue`를 거부한다. 운영자는 `put-secret-value`,
  `describe-secret`, `update-secret-version-stage`만 사용하고 값 재조회는
  시도하지 않는다.

## 2. Verifier

별도 verifier binary는 두지 않는다. cert-loader가 AWSCURRENT 배포와 동일한
코드 경로로 verifier 역할을 한다.

- 오프라인(사전) 검증: `scripts/pki/nonprod/pki.py`의 `verify`와 `bundle`이
  chain, validity, key match, exact SPIFFE/DNS/EKU를 검증한 뒤에만 bundle
  JSON을 만든다.
- AWS(사후) 검증: 서비스 task definition으로 standalone canary task를 실행하되
  cert-loader command만 `--version-stage AWSPENDING`으로 override한다. canary는
  ECS service 밖이므로 Cloud Map에 등록되지 않고 트래픽을 받지 않는다.
  loader 성공 + application health가 pending bundle의 합격 판정이다.

## 3. 서비스 계약 참조

loader command override에 사용하는 값은 다음과 같다. `<account>`는 12자리
계정, secret ARN suffix는 Secrets Manager가 생성한 6자를 그대로 쓴다.

| Service | `--expected-service` | `--expected-dns-san` | `--expected-eku` |
| --- | --- | --- | --- |
| bff | `bff` | 없음 | `clientAuth` |
| auth | `auth` | `auth.meetingmind.internal` | `serverAuth` |
| core | `core` | `core.meetingmind.internal` | `clientAuth`, `serverAuth` |
| ai | `ai` | `ai.meetingmind.internal` | `serverAuth` |
| realtime-stt | `stt` | `stt.meetingmind.internal` | `serverAuth` |

`--expected-spiffe-id`는
`spiffe://meetingmind.internal/ns/nonprod-v2/sa/{serviceAccount}`이며
serviceAccount는 `scripts/pki/nonprod/manifests/*.json`의 값과 같다.
`--expected-eku`는 manifest 순서대로 반복하고 `--output-dir`는 항상
`/run/meetingmind/tls`다.

## 4. Leaf rotation (서비스 1개 단위)

### 4.1 발급과 오프라인 검증

repository 밖 경로에서 수행한다.

```bash
python3 scripts/pki/nonprod/pki.py issue \
  --ca-dir <external-ca-dir> \
  --manifest scripts/pki/nonprod/manifests/<service>.json \
  --output <external-work-dir>/issued-<service> \
  --intermediate-passphrase-file <external-passphrase-file>

python3 scripts/pki/nonprod/pki.py bundle \
  --manifest scripts/pki/nonprod/manifests/<service>.json \
  --certificate <external-work-dir>/issued-<service>/certificate.pem \
  --private-key <external-work-dir>/issued-<service>/private-key.pem \
  --ca-bundle <external-work-dir>/issued-<service>/ca-bundle.pem \
  --output <external-work-dir>/<service>-bundle.json
```

`bundle` 출력의 `fingerprintSha256`을 evidence에 기록한다.

### 4.2 `AWSPENDING` 입력

```bash
aws secretsmanager put-secret-value \
  --secret-id "arn:aws:secretsmanager:ap-northeast-2:<account>:secret:/meetingmind-nonprod-v2/<service>/tls-bundle-XXXXXX" \
  --secret-string "file://<external-work-dir>/<service>-bundle.json" \
  --version-stages AWSPENDING
```

출력의 `VersionId`를 새 version ID로 기록한다. 현재 `AWSCURRENT` version ID는
`aws secretsmanager describe-secret --query VersionIdsToStages`로 확인한다.

### 4.3 `AWSPENDING` canary 검증

```bash
aws ecs run-task \
  --cluster meetingmind-nonprod-v2 \
  --task-definition meetingmind-nonprod-v2-<service> \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[<private-subnet-ids>],securityGroups=[<service-sg-id>],assignPublicIp=DISABLED}" \
  --overrides '{"containerOverrides":[{"name":"cert-loader","command":["--secret-arn","<tls-bundle-arn>","--version-stage","AWSPENDING","--expected-service","<loader-service>","--expected-spiffe-id","<spiffe-id>","--expected-dns-san","<dns-san>","--expected-eku","<eku>","--output-dir","/run/meetingmind/tls"]}]}'
```

- subnet/SG 값은 `terraform output private_subnet_ids`와 해당 서비스 SG를
  사용한다. BFF는 `--expected-dns-san`을 생략하고 Core는 `--expected-eku`를
  `clientAuth`, `serverAuth` 순서로 두 번 넣는다.
- 합격: cert-loader 로그가 `certificate_loaded fingerprint_sha256=...`이고
  fingerprint가 4.1의 값과 같으며 application container가 healthy로 전이한다.
- 불합격: `cert_loader_failed code=...` 또는 application 기동 실패. 새 bundle을
  수정해 4.2부터 반복하고 `AWSCURRENT`는 그대로 둔다.
- 검증 후 canary task를 `aws ecs stop-task`로 종료한다.

### 4.4 `AWSCURRENT` 승격과 배포

```bash
aws secretsmanager update-secret-version-stage \
  --secret-id "<tls-bundle-arn>" \
  --version-stage AWSCURRENT \
  --move-to-version-id "<new-version-id>" \
  --remove-from-version-id "<old-version-id>"

aws secretsmanager update-secret-version-stage \
  --secret-id "<tls-bundle-arn>" \
  --version-stage AWSPENDING \
  --remove-from-version-id "<new-version-id>"

aws ecs update-service \
  --cluster meetingmind-nonprod-v2 \
  --service meetingmind-nonprod-v2-<service> \
  --force-new-deployment
```

이전 version은 자동으로 `AWSPREVIOUS`가 된다. 해당 서비스만 배포하고 확인
전에는 다음 서비스로 넘어가지 않는다.

### 4.5 배포 확인

- 새 task의 cert-loader 로그 fingerprint가 새 leaf와 일치한다.
- deployment가 `COMPLETED`이고 circuit breaker rollback이 없다.
- caller/callee 요청이 성공한다 (예: Core→AI, BFF→Auth).
- evidence 표(7장)를 기록한다.

## 5. Leaf rollback

canary 불합격은 rollback이 아니다. `AWSCURRENT` 승격 이후 문제가 발견된
경우에만 수행한다.

```bash
aws secretsmanager update-secret-version-stage \
  --secret-id "<tls-bundle-arn>" \
  --version-stage AWSCURRENT \
  --move-to-version-id "<previous-version-id>" \
  --remove-from-version-id "<bad-version-id>"

aws ecs update-service \
  --cluster meetingmind-nonprod-v2 \
  --service meetingmind-nonprod-v2-<service> \
  --force-new-deployment
```

새 task의 loader fingerprint가 이전 값으로 복귀했는지 확인하고 evidence를
기록한다. 실패한 version은 원인 분석 전까지 삭제하지 않는다.

## 6. CA overlap rotation (3단계)

새 offline root/intermediate CA를 준비한 뒤 수행한다. 세 단계를 한 deployment로
합치지 않으며, 기존 CA가 만료되기 전에 3단계까지 완료한다. cert-loader와
pki.py는 `caBundlePem`의 `(intermediate, root)` 쌍을 정상 상태에서 1개,
overlap window에서만 2개 허용하고 중복·홀수·만료 쌍은 거부한다.

### 단계 1: old+new trust 확장

1. 서비스별로 기존 leaf/key를 유지한 채 `--ca-bundle`에 old와 new
   `(intermediate, root)`를 이어 붙인 파일을 넘겨 bundle JSON을 재생성한다.

   ```bash
   cat <old-ca>/ca-bundle.pem <new-ca>/ca-bundle.pem \
     > <external-work-dir>/overlap-ca-bundle.pem
   ```

2. 각 서비스에 4장의 leaf rotation 절차(4.2~4.5)를 적용한다.
3. 완료 기준: 5개 서비스 전부 old+new trust로 배포되고 기존 caller/callee
   통신이 유지된다.

### 단계 2: 새 CA leaf 배포

1. 새 CA로 서비스별 leaf를 발급하되 `--ca-bundle`은 계속 overlap 파일을 쓴다.
2. Auth → AI/Realtime STT → Core → BFF 순서로 서비스마다 4장의 절차를
   적용한다.
3. 완료 기준: 모든 서비스의 loader fingerprint가 새 CA leaf이고 통신이
   유지된다. 이 단계까지는 old CA leaf로 rollback할 수 있다.

### 단계 3: old trust 제거

1. 모든 서비스의 현재 fingerprint가 새 CA leaf임을 evidence로 확인한다.
2. `--ca-bundle`을 새 CA의 `(intermediate, root)` 한 쌍으로 되돌려 bundle을
   재생성하고 서비스별로 4장의 절차를 적용한다.
3. 완료 기준: 전 서비스가 새 CA 단독 trust로 배포되고 통신이 유지된다.
   이후 old CA leaf는 어디서도 수락되지 않는다.

단계 3 이후 되돌려야 하면 단계 1을 역방향(old+new trust 재확장)으로 반복한 뒤
old CA leaf를 재배포한다.

## 7. Evidence 템플릿

| UTC 시각 | 단계 | Service | Secret version ID | Leaf fingerprint SHA-256 | Task definition revision | Deployment 상태 | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- |

PEM, private key, passphrase, secret 값, `get-secret-value` 출력은 기록을
금지한다.

## 8. 실행 경계

- 이 runbook의 명령은 T048-V private validation deployment가 준비되고 Phase 8
  사용자 승인이 있을 때만 실행한다.
- drill evidence(leaf canary/승격/rollback, CA overlap 3단계)는 수집 즉시
  `specs/002-bff-auth-msa/implement.md`에 기록하고, 그 전에는 T047-B4를 완료로
  표시하지 않는다.
