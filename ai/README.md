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

`ai/.env`가 없으면 `../backend/.env`도 fallback으로 읽습니다.

## Embedding worker

로컬 PostgreSQL에 V12 migration이 적용된 후 아래 명령으로 실행합니다.

```bash
python -m app.embedding_worker
```

Docker에서는 루트에서 `docker compose -f compose.local.yml --profile ai up --build`를 사용합니다.

## Observability

AI API, PostgreSQL 검색, embedding worker는 `traceId`가 포함된 JSON 구조 로그를 남깁니다. 로그에는 질문, STT, 답변, API key, service token 원문을 넣지 않습니다. Backend가 전달한 `X-Request-ID`는 AI 응답과 내부 로그까지 유지됩니다.

초기 운영 알림 기준은 다음과 같습니다. 실제 트래픽 기준선이 쌓이면 T263 검증 결과로 조정합니다.

- 검색 지연 p95가 5분 동안 1초 초과
- `unsupported=true` 비율이 최소 20건 기준 15분 동안 30% 초과
- `citationFailure=true` 비율이 15분 동안 5% 초과
- embedding queue의 가장 오래된 pending job이 300초 초과하거나 failed job이 1건 이상
- AI gateway/provider 실패율이 5분 동안 5% 초과
