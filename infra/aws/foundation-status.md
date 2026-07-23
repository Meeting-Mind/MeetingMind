# AWS Foundation Status

이 문서는 2026-07-21과 2026-07-23 사용자가 공유한 AWS 콘솔 작업 현황을 기준으로 한 MeetingMind AWS foundation 상태다. 완료 표시는 사용자 확인 기준이며 이 문서 갱신 과정에서 AWS 계정을 직접 조회하지 않았다.

## Current Status

| Step | Status | Completed | Remaining |
| --- | --- | --- | --- |
| 1. Control Tower validation | Almost complete | 랜딩 존 활성화, 서울 홈 리전, Security/Sandbox/Production OU 등록, 보안 계정과 Prod/NonProd 계정 생성, 기본 제어 적용 | CloudTrail 로그가 LogArchive 계정 S3에 실제 적재되는지, Config 수집 상태, 알림 메일 최종 확인 |
| 2. Identity Center security | Complete | SSO 관리자 사용자, MFA 강제, 인증 앱/패스키 등록, 포털 로그인 확인, Prod/NonProd 관리자 접근 확인, 불필요 사용자 삭제 | 배포 후 업무별 최소 권한 세분화 |
| 3. Break-glass access | Mostly complete | BreakGlass 사용자/그룹 생성, 별도 암호, TOTP/패스키 2개, 로그인 테스트, access key 없음 | BreakGlass 로그인 발생 시 알림 구성 |
| 4. Prod/NonProd separation | Complete | Production OU와 Sandbox OU, `MeetingMind-Prod`, `MeetingMind-NonProd` 계정 생성 및 올바른 OU 배치 | 실제 리소스를 각 계정에 분리 배포 |
| 5. Organization security services | Complete | GuardDuty 활성화, SecurityAudit 위임 관리자, 조직 계정 자동 활성화, Security Hub 중앙 관리, 서울 리전/OU 대상 보안 정책 적용 | 탐지 결과가 쌓이면 오탐 조정 및 알림 연동 |
| 6. Cost baseline | Complete | Cost Anomaly Monitor와 일일 알림, 시드니 리전의 주요 과금 리소스 점검 | 24시간 이후 비용 데이터 표시 확인, 필요 시 추가 예산 알림 |
| 7. Application infrastructure | In progress | NonProd VPC/subnet/route, NAT Gateway/private route, S3 Gateway Endpoint, ECR/lifecycle, ECS Fargate cluster와 IAM/SG/Log Group 기반 구성 | 이미지 push, Task Definition, ALB/Listener/Target Group, ECS Service, discovery/secrets, health/alert/autoscaling 검증 |

## Current Working Account

- Account: `MeetingMind-NonProd`
- Permission set: `AWSAdministratorAccess`
- Region: Seoul `ap-northeast-2`
- Current infrastructure step: ECS Fargate service deployment preparation

## Completed ECS Foundation

- NonProd 전용 단일 ECS Fargate cluster
- ECR repositories: `bff`, `auth`, `core`, `ai`, `realtime-stt`
- ECR lifecycle policy와 immutable Git commit SHA tag 기준
- NonProd 기본 scan-on-push 활성, Inspector enhanced ECR scanning 비활성
- ECS service-linked role
- 공통 ECS Task Execution Role
- BFF/Auth/Core/AI/realtime-stt 서비스별 Task Role
- 서비스별 Security Group
- 서비스별 CloudWatch Log Group, retention 7 days
- NAT Gateway와 private subnet route
- S3 Gateway Endpoint

수작업 NonProd 리소스 태그 기준:

- `Project=meetingmind`
- `Environment=nonprod`
- `ManagedBy=manual`
- `Service=<서비스명>`

## Immediate Next Steps

- BFF/Auth/Core/AI/STT image build and ECR push
- 서비스별 Task Definition 등록
- Public ALB, Target Group과 Listener 구성
- BFF/Auth/Core/AI/STT ECS Service 생성과 2개 AZ private app subnet 배치
- BFF→Auth/Core, Core→AI, Core→STT service discovery 확정
- Secrets Manager/Parameter Store 연동
- health check, logs, alarms와 Service Auto Scaling 검증
- `realtime-stt`는 독립 서비스로 배포 준비 중이며 컨테이너 포트는 `8083`
- STT 배포 전 Actuator health endpoint, STT 전용 DB/Secrets, Core SG→STT SG TCP 8083 허용 검증 필요

초기 VPC console 절차는 `infra/aws/nonprod/network/console-checklist.md`, 현재 작업 상태는 `specs/002-bff-auth-msa/tasks.md`를 기준으로 한다.
