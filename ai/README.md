# AI Service

MeetingMind의 AI 전용 서비스입니다.

## Run

```bash
cd /Users/miju/final/ai
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Env

- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `OPENAI_EMBEDDING_MODEL` (default: `text-embedding-3-small`)
- `OPENAI_EMBEDDING_DIMENSION` (default: `1536`)
- `AI_DATABASE_URL` (AI 검색과 embedding worker가 공유하는 PostgreSQL DSN)
- `AI_INTERNAL_SERVICE_TOKEN` (Backend와 AI가 동일하게 설정하는 internal API credential)
- `AI_QUEUE_OBSERVATION_SECONDS` (embedding queue snapshot 주기, 기본 60초)

설정 우선순위는 process 환경변수, `ai/.env`, 루트 `.env`, `backend/.env` 순서다. `OPEN_AI_KEY`는 기존 로컬 설정 호환을 위한 `OPENAI_API_KEY` 별칭이다.

## Embedding worker

로컬 PostgreSQL에 V12 migration이 적용된 후 아래 명령으로 실행합니다.

```bash
python -m app.embedding_worker
```

Docker에서는 루트에서 `docker compose -f compose.local.yml --profile ai up --build`를 사용합니다.

실제 provider 연결은 비용이 발생할 수 있으므로 opt-in smoke test로만 확인한다.

```bash
RUN_OPENAI_EMBEDDING_SMOKE=true python -m unittest tests.test_openai_embedding_smoke
```

이 테스트는 `OPENAI_API_KEY`, `OPENAI_EMBEDDING_MODEL`, `OPENAI_EMBEDDING_DIMENSION`을 사용해 한국어 문장 1건의 vector 차원을 확인한다. 기본 test suite에서는 skip된다.

Flyway V12 이상이 적용된 별도 PostgreSQL 데이터베이스에서는 실제 STT 입력, worker 색인, Project scope 제한, cross-space 차단과 DB retrieval p95를 함께 확인할 수 있다.

```bash
RUN_OPENAI_RAG_INTEGRATION=true \
AI_TEST_DATABASE_URL=postgresql://meetingmind:meetingmind_local@localhost:5434/meetingmind_rag_eval \
./.venv/bin/python -m unittest tests.test_openai_rag_integration
```

이 테스트는 API 호출 비용이 발생하며, `AI_TEST_DATABASE_URL`은 embedding job이 없는 전용 평가 DB여야 한다. 생성한 테스트 데이터는 종료 시 삭제한다. 100회 p95는 OpenAI query embedding 시간을 제외한 PostgreSQL hybrid retrieval 경계만 측정한다.

한국어 grounded 응답 30건의 false-supported 비율, citation 정확도와 provider 포함 지연은 아래 opt-in 평가로 측정한다.

```bash
RUN_OPENAI_GROUNDED_EVAL=true ./.venv/bin/python -m unittest tests.test_openai_grounded_evaluation
```

15개 근거 있음 질문은 source citation을 확인하고, 15개 근거 없음 질문은 `unsupported=true`가 아닌 응답을 false-supported로 센다.

## Observability

AI API, PostgreSQL 검색, embedding worker는 `traceId`가 포함된 JSON 구조 로그를 남깁니다. 로그에는 질문, STT, 답변, API key, service token 원문을 넣지 않습니다. Backend가 전달한 `X-Request-ID`는 AI 응답과 내부 로그까지 유지됩니다.

초기 운영 알림 기준은 다음과 같습니다. 실제 트래픽 기준선이 쌓이면 T263 검증 결과로 조정합니다.

- 검색 지연 p95가 5분 동안 1초 초과
- `unsupported=true` 비율이 최소 20건 기준 15분 동안 30% 초과
- `citationFailure=true` 비율이 15분 동안 5% 초과
- embedding queue의 가장 오래된 pending job이 300초 초과하거나 failed job이 1건 이상
- AI gateway/provider 실패율이 5분 동안 5% 초과
