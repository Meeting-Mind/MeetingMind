-- embedding_jobs는 실패 원인을 failure_code 하나로만 남긴다. normalize_failure_code가
-- 미분류 예외를 전부 INTERNAL_ERROR로 접기 때문에, 실패한 행만 보고는 원인을 알 수 없다.
-- 실제로 dev DB의 INTERNAL_ERROR 3건은 재실행으로만 환경성 실패임을 좁힐 수 있었다.
--
-- 예외 메시지는 저장하지 않는다. provider 응답 본문이나 DSN(비밀번호 포함)이 예외 메시지에
-- 실려 올 수 있어 NFR-LOG-01 원문 비노출 원칙과 충돌한다. 대신 예외의 정규화된 타입 이름만
-- 남긴다. 이것만으로도 psycopg 오류와 provider 오류를 즉시 구분할 수 있다.

alter table embedding_jobs
    add column failure_detail varchar(200);

alter table embedding_jobs
    add constraint embedding_jobs_failure_detail_check
        check (failure_detail is null or failure_code is not null);

comment on column embedding_jobs.failure_detail is
    'Qualified exception type only. Never store provider or driver messages.';
