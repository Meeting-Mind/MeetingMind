# MeetingMind DNS root

`meetingmind.co.kr` 퍼블릭 호스팅 영역을 소유하는 root다. 도메인 **등록**은 가비아에 그대로 두고
**DNS 호스팅**만 Route 53으로 옮긴다. 등록기관 이전은 하지 않는다.

## 왜 별도 root인가

호스팅 영역은 개별 환경보다 오래 산다. `environments/nonprod-v2`에 넣으면 그 환경을 destroy할 때
영역이 함께 지워지고, 재생성된 영역은 **다른 네임서버**를 받는다. 그러면 가비아에서 네임서버를 다시
바꿀 때까지 도메인 전체가 죽는다. `aws_route53_zone.root`에 `prevent_destroy = true`를 건 이유도
같다. 환경 root는 이 영역을 `data` 소스로 읽기만 한다.

## 이전 절차 (순서를 지킬 것)

`aws_acm_certificate_validation`은 DNS 검증이 끝날 때까지 대기한다. 위임 전에 실행하면 ACM이
Route 53의 검증 레코드를 보지 못해 타임아웃까지 멈췄다가 실패한다. 그래서 단계를 나눈다.

### Stage 1 — 영역과 레코드 생성 (이 root, 무중단) — 2026-07-27 완료

호스팅 영역 `Z053964618TP9D9ZGOZSS`.

```bash
terraform init -backend-config=backend.hcl
terraform plan
terraform apply
terraform output registrar_nameservers
```

이 시점에는 실제 조회 결과가 바뀌지 않는다. 도메인은 여전히 가비아 네임서버로 응답한다.

### Stage 2 — 가비아에서 네임서버 교체 (수작업) — 2026-07-27 완료

`domains.gabia.com` → 도메인 관리 → 네임서버 변경 → 대상 도메인만 선택 → `네임서버`(외부) 라디오 →
1~4차에 Stage 1 output 입력 → 소유자 인증. 서비스 중단 없이 전환됐다.

- 롤백 값: `ns.gabia.co.kr`, `ns.gabia.net`, `ns1.gabia.co.kr`
- 가비아의 기존 DNS 레코드(`app` CNAME, ACM 검증 CNAME)는 2026-08-03까지 롤백용으로 남긴다.

전파 확인:

```bash
dig NS meetingmind.co.kr +short                     # 리졸버 캐시 기준
dig @b.dns.kr NS meetingmind.co.kr +norecurse       # .kr 레지스트리 위임 기준
```

레지스트리 위임 TTL이 86400이라 캐시된 리졸버는 최대 하루 늦게 따라온다. 그동안에도 양쪽 응답이
같은 CloudFront를 가리키므로 접속에는 영향이 없다.

### Stage 3 — ACM을 Terraform 관리로 — 발급 완료, 전환 대기

`acm.tf`가 `app.meetingmind.co.kr` 인증서를 us-east-1에 발급하고 DNS 검증까지 끝낸다. ARN은
`app_certificate_arn` output으로 나온다.

```
arn:aws:acm:us-east-1:825820234979:certificate/de22f099-addb-4b9b-929f-6f2e9003a291
```

ACM은 같은 도메인에 대해 검증 CNAME 이름을 재사용한다. 그래서 이 인증서의 검증 레코드는 외부
발급 인증서가 쓰던 레코드와 **같은 레코드**다. 두 리소스가 한 레코드를 관리하는 상태를 없애려고
`legacy_acm_validation_records`는 `terraform state rm`으로 추적만 끊고 제거했다. config에서 그냥
지웠다면 레코드가 삭제되어 새 인증서의 자동 갱신이 깨졌을 것이다.

**남은 전환 작업** — `environments/nonprod-v2`에서 수행한다.

```hcl
frontend_custom_domain = {
  name                = "app.meetingmind.co.kr"
  acm_certificate_arn = "arn:aws:acm:us-east-1:825820234979:certificate/de22f099-addb-4b9b-929f-6f2e9003a291"
}
```

`terraform apply` 후 CloudFront 배포 반영(약 10분)을 기다리고, 아래로 확인한 뒤 기존 인증서
`da6b3700-51f0-4e72-8fb4-e3b456cdb962`를 삭제한다. 사용 중인 인증서는 ACM이 삭제를 거부하므로
`InUse`가 비워진 것을 먼저 확인한다.

```bash
curl -sI https://app.meetingmind.co.kr | head -1
aws acm list-certificates --region us-east-1 \
  --query 'CertificateSummaryList[].{Arn:CertificateArn,InUse:InUse}' --output table
```

`nonprod-v2`는 상태 파일을 다른 작업과 공유하므로, apply 시점에 워킹트리의 다른 변경이 함께
배포된다는 점을 확인하고 진행한다.

## 현재 상태

- 가비아 존에 있던 레코드는 `app` CNAME과 ACM 검증 CNAME 둘뿐이었다. MX·apex 레코드가 없어
  이전 시 끊길 메일 트래픽이 없었다.
- `app`은 CNAME 대신 A/AAAA ALIAS로 승격했다. CloudFront는 IPv6가 켜져 있어 AAAA도 필요하다.
- ACM 검증 CNAME은 `legacy_acm_validation_records`로 그대로 옮겼다. 이 레코드가 없으면 인증서
  자동 갱신이 조용히 실패한다. 갱신 시도는 만료 60일 전(2026-12-11경)에 시작된다.
- ALB HTTPS listener와 WAF는 이 root의 범위가 아니다. `environments/nonprod-v2/README.md`의
  release gate를 따른다.

## apex를 쓰지 않는 이유

`meetingmind.co.kr`에는 레코드를 두지 않는다. 서비스 호스트는 `app.meetingmind.co.kr` 하나다.

BFF 세션 쿠키가 `__Host-mm-session`(`bff/src/main/resources/application.yml`)이라 `Domain` 속성을
가질 수 없고, 호스트마다 세션이 완전히 분리된다. 같은 앱을 apex와 `app.` 양쪽에서 서빙하면 한쪽에서
로그인해도 다른 쪽은 로그아웃 상태가 되고, `app.`으로 등록된 Google OAuth redirect URI도 맞지 않는다.

apex 접속을 지원해야 할 때는 앱을 이중 서빙하지 말고 CloudFront Function에서 `app.`으로 301
리다이렉트한다. 그때는 인증서 SAN에 apex 추가, CloudFront `aliases` 추가, Route 53 apex ALIAS를
**이 순서대로** 적용해야 한다. DNS를 먼저 열면 CloudFront가 `403`을 반환한다.
