# Terraform state bootstrap

이 root는 application stack과 분리된 로컬 state로 한 번 실행한다.

```bash
export AWS_PROFILE=meetingmind-nonprod
terraform init
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

출력의 `backend_hcl` 값을 `infra/aws/environments/nonprod-v2/backend.hcl`에 옮긴다. `backend.hcl`, local state와 saved plan은 커밋하지 않는다.

State bucket과 KMS key에는 `prevent_destroy`가 적용된다. 폐기하려면 코드 검토와 별도 승인으로 protection을 먼저 해제해야 한다.
