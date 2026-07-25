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

- `AI_TEXT_PROVIDER` (default: `openai`, values: `openai`, `local-openai-compatible`; `local`, `openai-compatible` aliases are reported as `local-openai-compatible`)
- `AI_TEXT_BASE_URL` (local OpenAI-compatible server base URL, e.g. `http://llm.internal:8000/v1`)
- `AI_TEXT_API_KEY` (local provider bearer token; default: `local-provider`)
- `AI_TEXT_MODEL` (local provider model name)
- `AI_TEXT_API_STYLE` (default: `responses`, values: `responses`, `chat-completions`)
- `AI_TEXT_STREAM` (default: `false`; local `chat-completions` streaming enables real TTFT measurement)
- `AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE` (default: `false`; local streaming request에 `stream_options.include_usage` 포함 여부)
- `AI_TEXT_RESPONSE_FORMAT_MODE` (default: `json_schema`, values: `json_schema`, `json_object`, `none`)
- `AI_EMBEDDING_PROVIDER` (default: `openai`, values: `openai`, `local-openai-compatible`; `local`, `openai-compatible` aliases are reported as `local-openai-compatible`)
- `AI_EMBEDDING_BASE_URL` (local OpenAI-compatible embedding server base URL)
- `AI_EMBEDDING_API_KEY` (local embedding provider bearer token; default: `local-provider`)
- `AI_EMBEDDING_MODEL` (local embedding model name)
- `AI_EMBEDDING_DIMENSION` (local embedding vector dimension, default: `1536`)
- `AI_EMBEDDING_INCLUDE_DIMENSIONS` (default: `false`; local embedding request에 `dimensions` 파라미터 포함 여부)
- `AI_VECTOR_DIMENSION` (PostgreSQL `embedding_chunks.embedding` schema dimension, default: `1536`)
- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `OPENAI_BASE_URL` (default: `https://api.openai.com/v1`)
- `OPENAI_EMBEDDING_MODEL` (default: `text-embedding-3-small`)
- `OPENAI_EMBEDDING_DIMENSION` (default: `1536`)
- `OPENAI_EMBEDDING_INCLUDE_DIMENSIONS` (default: `true`)
- `AI_DATABASE_URL` (AI 검색과 embedding worker가 공유하는 PostgreSQL DSN)
- `AI_INTERNAL_AUTH_MODE` (`shared-token` 기본값 | `mtls-proxy`. ECS NonProd V2는 `mtls-proxy`를 사용하고 `/api/internal/**`에서 Envoy가 재작성한 단일 XFCC의 exact SPIFFE URI만 수락한다)
- `AI_INTERNAL_ALLOWED_SPIFFE_ID` (`mtls-proxy`에서 허용하는 유일한 caller SPIFFE URI. 미설정이면 모든 internal 요청을 거부한다)
- `AI_INTERNAL_SERVICE_TOKEN` (`shared-token` mode에서 Backend와 AI가 동일하게 설정하는 internal API credential. local/on-prem PoC 호환 경계이며 ECS task definition에는 넣지 않는다)
- `AI_QUEUE_OBSERVATION_SECONDS` (embedding queue snapshot 주기, 기본 60초)
- `ONPREM_POC_RAG_QUERY` (온프레 smoke의 DB retrieval latency probe query, 기본 smoke query)
- `ONPREM_POC_PROJECT_ID` (온프레 smoke의 DB retrieval projectId; `ONPREM_POC_REQUIRE_RETRIEVAL=true`이면 필수)
- `ONPREM_POC_ALLOWED_MEETING_IDS` (온프레 smoke의 DB retrieval allowed meeting ids, comma-separated; `ONPREM_POC_REQUIRE_RETRIEVAL=true`이면 최소 1개 이상 필수)
- `ONPREM_POC_REQUIRE_RETRIEVAL` (default: `false`; `true`이면 DB retrieval latency 미측정 시 smoke 실패)
- `ONPREM_POC_RESULT_PATH` (optional; smoke JSON 결과를 저장할 파일 경로)

설정 우선순위는 process 환경변수, `ai/.env`, 루트 `.env`, `backend/.env` 순서다. `OPEN_AI_KEY`는 기존 로컬 설정 호환을 위한 `OPENAI_API_KEY` 별칭이다.

기본값은 기존 OpenAI provider다. 온프레 PoC에서는 FastAPI route, prompt, report/task parsing, citation validation, RAG retrieval, embedding job/generation/swap 로직을 바꾸지 않고 provider만 아래처럼 전환한다.

최종 PoC 실행용 템플릿은 `ai/onprem.env.example`이다. 실제 local LLM/embedding endpoint, model, token, 평가 DB 값을 채운 뒤 `ai/.env`로 복사하거나 `./onprem_poc_run.sh ./onprem.env`처럼 wrapper에 명시해 사용한다. 템플릿의 `local-llm-model`, `local-embedding-model` 같은 placeholder 모델명은 preflight-only 확인에는 쓸 수 있지만 최종 smoke preflight와 validator에서는 실패한다. 아래 예시의 `qwen2.5-14b-instruct`, `bge-m3`도 예시일 뿐이며, 최종 validator에서는 provider 응답에서 관측한 모델명이 설정 모델명과 일치해야 한다. AI 컨테이너에서 host의 모델 서버를 호출하는 경우 `localhost` 대신 `host.docker.internal` 또는 온프레 네트워크 DNS 이름을 사용한다.

`local-openai-compatible` provider의 base URL은 절대 http(s) URL이어야 하며 `api.openai.com`을 가리키면 provider 초기화와 smoke preflight에서 실패한다. Base URL에는 credential userinfo, query string, fragment를 넣지 않는다. `/health`와 smoke result의 local-compatible boolean도 같은 판정 helper를 사용한다. 실제 OpenAI endpoint를 쓰는 경우에는 provider를 `openai`로 명시한다.

```bash
AI_TEXT_PROVIDER=local-openai-compatible
AI_TEXT_BASE_URL=http://llm.internal:8000/v1
AI_TEXT_API_KEY=local-token
AI_TEXT_MODEL=qwen2.5-14b-instruct
AI_TEXT_API_STYLE=responses
AI_TEXT_STREAM=false
AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE=false
AI_TEXT_RESPONSE_FORMAT_MODE=json_schema

AI_EMBEDDING_PROVIDER=local-openai-compatible
AI_EMBEDDING_BASE_URL=http://embedding.internal:8001/v1
AI_EMBEDDING_API_KEY=local-token
AI_EMBEDDING_MODEL=bge-m3
AI_EMBEDDING_DIMENSION=1536
AI_EMBEDDING_INCLUDE_DIMENSIONS=false
AI_VECTOR_DIMENSION=1536
```

`AI_TEXT_RESPONSE_FORMAT_MODE=json_schema`가 기본값이며 기존 strict JSON schema 의도를 가장 잘 보존한다. local LLM 서버가 `json_schema`를 거부하면 `json_object`로 낮출 수 있고, 이마저 지원하지 않는 서버는 `none`으로 request parameter만 생략한다. 어느 모드에서도 기존 prompt, JSON parsing, citation validation은 그대로 실행되므로 malformed JSON은 기존 provider/parsing 실패로 처리된다.

최종 온프레 smoke는 TTFT를 실제로 측정해야 하므로 local text provider에서 `AI_TEXT_API_STYLE=chat-completions`와 `AI_TEXT_STREAM=true`를 요구한다. `AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE=false`가 local provider 기본값이다. 일부 OpenAI-compatible 서버는 streaming은 지원하지만 `stream_options`를 거부한다. 사용량 chunk를 지원하는 서버에서만 `true`로 켠다. 꺼져 있어도 TTFT는 실제 streaming 첫 token 기준으로 측정되고, validator는 이 값이 0 이상 숫자인지 확인한다. `tokensPerSecond`는 usage가 없으면 출력 텍스트 길이 기반 estimate로 계산된다. Streaming SSE의 keep-alive/comment와 role-only chunk는 허용하지만, `data:` payload가 JSON object가 아니면 provider invalid response로 실패시킨다.

`AI_EMBEDDING_DIMENSION`이 현재 DB의 `embedding_chunks.embedding vector(1536)`과 다르면 AI server는 provider 초기화와 smoke preflight에서 실패한다. `AI_EMBEDDING_DIMENSION`, `OPENAI_EMBEDDING_DIMENSION`, `AI_VECTOR_DIMENSION`은 양의 정수여야 하며 잘못된 값은 provider error로 처리된다. Dimension이 달라지는 경우 새 재색인 시스템을 만들지 않고, schema migration과 기존 embedding job generation/swap 경로로 전체 재색인을 완료한 뒤 retrieval 평가를 통과시키고 `AI_VECTOR_DIMENSION`과 provider dimension을 함께 변경해야 한다. 일부 온프레 embedding 서버는 OpenAI-compatible endpoint를 제공해도 `dimensions` 요청 파라미터를 거부하므로 local provider의 기본값은 `AI_EMBEDDING_INCLUDE_DIMENSIONS=false`다. 이 경우에도 응답 item shape, vector 길이, finite numeric vector 값은 계속 검증한다.

`GET /health`는 새 API를 추가하지 않고 provider 전환 상태를 확인하기 위한 safe config만 반환한다. 응답에는 `text_provider`, `embedding_provider`, `text_base_url_configured`, `embedding_base_url_configured`, `text_base_url_local_compatible`, `embedding_base_url_local_compatible`, `text_api_style`, `text_stream`, `text_stream_options_include_usage`, `text_response_format_mode`, `embedding_dimension`, `vector_dimension`, `embedding_dimension_matches_vector`, `database_configured`, `internal_service_token_configured`가 포함되며 API key, bearer token, base URL, DSN 원문은 포함하지 않는다. `embedding_dimension_matches_vector`는 두 dimension이 모두 양수이고 같은 경우에만 `true`다.

## On-prem PoC checks

온프레 PoC는 기존 FastAPI route, prompt, parsing, citation validation, PostgreSQL/pgvector retrieval과 embedding worker를 그대로 사용한다. Provider 전환 뒤 아래 항목을 같은 endpoint에서 측정한다.

- TTFT: `AI_TEXT_API_STYLE=chat-completions`와 `AI_TEXT_STREAM=true`일 때 provider log의 0 이상 `ttftMs`
- 전체 응답 시간: endpoint log의 `durationMs`, provider log의 `totalMs`
- Tokens/sec: provider log의 `tokensPerSecond`
- Retrieval latency: retrieval log의 `durationMs`
- Citation 성공률: endpoint log의 `citationFailure=false` 비율
- JSON parsing 성공률: `AI_PROVIDER_UNAVAILABLE` 중 malformed structured output 비율
- Permission filter 유지: Backend scope envelope, `allowedMeetingIds`, cross-space negative test 유지

Local OpenAI-compatible provider가 준비되면 아래 smoke runner로 provider probe, embedding probe, Meeting AI, Project AI, report, task extraction, unsupported/hallucination guard, permission scope guard를 같은 기존 service 함수 경로에서 실행한다.

```bash
RUN_ONPREM_AI_POC_SMOKE=true \
AI_TEXT_PROVIDER=local-openai-compatible \
AI_TEXT_BASE_URL=http://localhost:8001/v1 \
AI_TEXT_API_KEY=local-token \
AI_TEXT_MODEL=qwen2.5-14b-instruct \
AI_TEXT_API_STYLE=chat-completions \
AI_TEXT_STREAM=true \
AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE=false \
AI_TEXT_RESPONSE_FORMAT_MODE=json_schema \
AI_EMBEDDING_PROVIDER=local-openai-compatible \
AI_EMBEDDING_BASE_URL=http://localhost:8002/v1 \
AI_EMBEDDING_API_KEY=local-token \
AI_EMBEDDING_MODEL=bge-m3 \
AI_EMBEDDING_DIMENSION=1536 \
AI_EMBEDDING_INCLUDE_DIMENSIONS=false \
AI_VECTOR_DIMENSION=1536 \
ONPREM_POC_REQUIRE_RETRIEVAL=true \
ONPREM_POC_RESULT_PATH=/tmp/meetingmind-onprem-poc-result.json \
./.venv/bin/python onprem_poc_smoke.py
```

Smoke runner는 네트워크 호출 전에 명백한 구성 오류를 먼저 중단한다. final smoke에서는 `AI_TEXT_PROVIDER=local-openai-compatible`과 `AI_EMBEDDING_PROVIDER=local-openai-compatible`이 필요하다. local provider 실행에서는 `AI_TEXT_BASE_URL`, placeholder가 아닌 `AI_TEXT_MODEL`, `AI_EMBEDDING_BASE_URL`, placeholder가 아닌 `AI_EMBEDDING_MODEL`이 필요하고, base URL은 절대 http(s) URL이어야 하며 `api.openai.com`, credential userinfo, query string, fragment를 포함하면 안 된다. `AI_EMBEDDING_DIMENSION`은 `AI_VECTOR_DIMENSION`과 같아야 한다. `ONPREM_POC_REQUIRE_RETRIEVAL=true`이면 `AI_DATABASE_URL`, 비어 있지 않은 `ONPREM_POC_PROJECT_ID`, 최소 1개 이상의 `ONPREM_POC_ALLOWED_MEETING_IDS`도 필수다. Placeholder 모델명은 `ONPREM_POC_PREFLIGHT_ONLY=true`에서만 허용된다.

Endpoint를 실제 호출하기 전에 env 파일만 확인하려면 preflight-only로 실행한다. 이 모드는 provider/RAG를 호출하지 않으며 validator용 최종 결과가 아니다. 저장소의 `ai/onprem.env.example`은 wrapper preflight-only 실행이 통과하고, final wrapper 실행은 placeholder 모델명 때문에 실패하도록 테스트로 고정되어 있다.

```bash
ONPREM_POC_PREFLIGHT_ONLY=true ./onprem_poc_run.sh ./onprem.env
```

Preflight-only 결과 JSON은 `onprem_poc_validate.py`에서 최종 결과로 인정하지 않는다.

동일한 환경변수를 설정한 뒤 smoke 실행과 validator 판정을 한 번에 수행하려면 wrapper를 사용할 수 있다. 이 스크립트는 첫 번째 인자 또는 `ONPREM_POC_ENV_FILE`로 env 파일을 받을 수 있고, `RUN_ONPREM_AI_POC_SMOKE=true`, `ONPREM_POC_REQUIRE_RETRIEVAL=true`, `ONPREM_POC_RESULT_PATH=/tmp/meetingmind-onprem-poc-result.json`을 기본값으로 둔다. Env file은 `KEY=VALUE`, `export KEY=VALUE`, 단순 single/double-quoted value를 지원하며, 이미 export된 shell 환경변수가 파일 값보다 우선한다. Env key는 shell identifier 형식만 허용하고 명령 치환은 실행하지 않는다. Wrapper는 외부 `ONPREM_POC_MIN_STARTED_AT` 값을 신뢰하지 않고 이번 실행 시작 시각을 validator 호출에 주입해 오래된 result JSON을 실수로 최종 gate에 쓰는 경우를 차단한다.

```bash
./onprem_poc_run.sh
# or
./onprem_poc_run.sh ./onprem.env
```

실환경 최종 검증은 아래 순서를 권장한다.

1. 평가 DB 준비

```bash
cd ai
ONPREM_POC_EVAL_DATABASE_NAME=meetingmind_onprem_eval_0722 \
./onprem_poc_prepare_eval_db.sh
```

2. `ai/onprem.env.example`를 기준으로 실제 `onprem.env` 작성
3. `AI_TEXT_MODEL`, `AI_EMBEDDING_MODEL`, `AI_TEXT_BASE_URL`, `AI_EMBEDDING_BASE_URL`, `AI_DATABASE_URL`, `AI_INTERNAL_SERVICE_TOKEN`, `ONPREM_POC_PROJECT_ID`, `ONPREM_POC_ALLOWED_MEETING_IDS`를 실제 값으로 치환
4. preflight-only로 env 검증

```bash
cd ai
ONPREM_POC_PREFLIGHT_ONLY=true \
ONPREM_POC_RESULT_PATH=/tmp/meetingmind-onprem-preflight.json \
./onprem_poc_run.sh ./onprem.env
```

5. preflight 결과에서 `config.textBaseUrlLocalCompatible`, `config.embeddingBaseUrlLocalCompatible`, `config.internalServiceTokenConfigured`, `config.allowedMeetingCount`를 확인
6. final smoke + validator 실행

```bash
cd ai
./onprem_poc_run.sh ./onprem.env
```

7. 필요하면 결과 파일 재검증

```bash
cd ai
./.venv/bin/python onprem_poc_validate.py /tmp/meetingmind-onprem-poc-result.json
```

실환경에서 자주 막히는 지점은 아래 네 가지다.

- `AI_TEXT_MODEL`, `AI_EMBEDDING_MODEL`이 placeholder 그대로 남아 있는 경우
- `AI_EMBEDDING_DIMENSION`과 `AI_VECTOR_DIMENSION`이 다르거나 0 이하인 경우
- `ONPREM_POC_REQUIRE_RETRIEVAL=true`인데 `ONPREM_POC_PROJECT_ID`, `ONPREM_POC_ALLOWED_MEETING_IDS`를 비워 둔 경우
- local provider base URL이 `api.openai.com`, relative path, userinfo credential, query string, fragment를 포함하는 경우

AI Docker image에도 `onprem_poc_smoke.py`, `onprem_poc_validate.py`, `onprem_poc_run.sh`가 포함된다. Compose로 띄운 컨테이너에서는 동일한 provider/DB 환경변수를 전달한 뒤 아래처럼 같은 gate를 실행할 수 있다.

```bash
docker compose -f compose.local.yml --profile ai exec meetingmind-ai ./onprem_poc_run.sh
```

출력은 JSON이며 `run`, `config`, `summary`, `metrics`를 포함한다. `ONPREM_POC_RESULT_PATH`가 있으면 stdout에 출력하는 동일 JSON을 파일에도 저장한다. `run.resultSchemaVersion`은 현재 `2`이며, local base URL compatible 판정 필드가 없는 v1 결과는 최종 validator에서 거부한다. `run`은 UTC 시작/완료 시각, smoke 전체 duration, preflight-only 여부를 담는다. `config`는 provider id, model, API style, stream, stream options usage 포함 여부, response format mode, embedding dimension, vector dimension, local base URL compatible 판정, DB/retrieval 설정 여부, internal service token 설정 여부처럼 결과 판정에 필요한 값만 담고, API key, bearer token, base URL, DSN 원문은 포함하지 않는다. `metrics`는 각 scenario의 `ok`, scenario 전체 소요 시간인 `durationMs`, `provider`, `model`, `sourceCount`, `itemCount`, `apiStyle`, `stream`, `responseFormatMode`, `providerTotalMs`, `ttftMs`, `tokensPerSecond`, `retrievalLatencyMs`, `hallucinationDetected`를 포함한다. `provider`는 `openai` 또는 `local-openai-compatible`처럼 실제 선택된 provider id다. `summary`는 `citationSuccessRate`, `jsonParsingSuccessRate`, `unsupportedGuardPassed`, `permissionGuardPassed`, `retrievalLatencyMeasured`, `retrievalRequired`, `retrievalRequirementPassed`, `maxRetrievalLatencyMs`, `hallucinationDetected`, `maxDurationMs`를 집계한다. 같은 실행 중 `ai_provider_completed` 구조 로그에도 동일한 `apiStyle`, `stream`, `responseFormatMode`, `ttftMs`, `totalMs`, `tokensPerSecond`가 남는다.

저장된 결과 파일은 아래 validator로 최종 gate를 다시 확인할 수 있다.

```bash
./.venv/bin/python onprem_poc_validate.py /tmp/meetingmind-onprem-poc-result.json
```

Validator는 run metadata, local text/embedding provider 사용, local base URL 구성 여부와 local-compatible 판정, model 구성 여부, placeholder가 아닌 실제 모델명, internal service token 설정 여부, wrapper 실행 시작 시각 이후 생성된 result 여부, result JSON 내 민감 필드명(`apiKey`, `token`, `baseUrl`, `databaseUrl`, `dsn` 등) 부재, text provider probe의 실제 JSON parse/shape, text provider probe와 Meeting/Project/Report/Task generation 응답에서 관측한 모델명의 설정 모델 일치, embedding provider 응답에서 관측한 모델명의 설정 모델 일치, `chat-completions` + streaming 기반 0 이상 TTFT 측정, generation scenario의 `responseFormatMode`와 config 일치, stream option과 embedding dimensions option의 boolean safe config, embedding/vector dimension 일치, 실제 embedding probe vector 길이와 provider id 일치, DB retrieval latency와 source 반환, retrieval scope config, 필수 9개 scenario 각각의 `durationMs`, provider/retrieval metric이 scenario `durationMs`를 넘지 않고 `run.durationMs`가 `summary.maxDurationMs` 이상인 시간 일관성, summary scenario count와 failed scenario 및 retrieval requirement를 포함한 metrics 재계산 값의 일치, 필수 9개 scenario만 중복 없이 포함한 metrics 목록, citation/JSON parsing 성공률 100%, unsupported guard, permission guard, hallucination proxy, Meeting/Project/Report/Task provider latency/token metric과 응답 모델 관측 여부, report/task 생성 itemCount를 검사한다. 이 validator가 실패하면 Day 2/3 완료로 보지 않는다.

성능 목표가 정해진 환경에서는 아래 optional threshold를 지정해 같은 결과 파일을 성능 gate로도 판정할 수 있다. TTFT와 tokens/sec threshold는 text provider probe뿐 아니라 Meeting AI, Project AI, report, task generation scenario 전체에 적용된다.

```bash
ONPREM_POC_MAX_TTFT_MS=2000 \
ONPREM_POC_MAX_TOTAL_MS=30000 \
ONPREM_POC_MAX_RETRIEVAL_MS=1000 \
ONPREM_POC_MIN_TOKENS_PER_SECOND=10 \
./.venv/bin/python onprem_poc_validate.py /tmp/meetingmind-onprem-poc-result.json
```

`AI_DATABASE_URL`이 설정되지 않은 smoke 실행은 `retrieval_latency_probe`를 `SKIPPED_NO_AI_DATABASE_URL`로 남기고 `summary.retrievalLatencyMeasured=false`를 반환한다. Day 2/3 완료 판단에는 실제 PostgreSQL/pgvector DB와 local embedding provider를 연결하고 `ONPREM_POC_REQUIRE_RETRIEVAL=true`로 실행해 `summary.retrievalRequirementPassed=true`가 되어야 한다. `hallucinationDetected`는 smoke fixture 기준의 proxy다. Positive scenario가 citation/source 없이 성공하거나, 근거 밖 질문이 unsupported로 차단되지 않으면 `true`가 되고 smoke는 실패한다.

Smoke scenario 기준:

- `text_provider_probe`: local/OpenAI-compatible text endpoint 연결과 JSON parse/shape 검증
- `embedding_provider_probe`: embedding endpoint 연결과 `AI_VECTOR_DIMENSION` 일치 여부
- `retrieval_latency_probe`: `AI_DATABASE_URL`이 있을 때 기존 PostgreSQL/pgvector retrieval 경로의 latency 측정
- `meeting_ai`, `project_ai`, `report`, `task`: 기존 prompt, parsing, citation validation 재사용 경로의 positive case
- `meeting_ai_unsupported`: 근거 밖 질문을 unsupported로 처리하는지 확인하는 hallucination guard
- `project_ai_permission_guard`: `allowedMeetingIds` 밖 meeting source가 기존 scope validation에서 403으로 차단되는지 확인 (`statusCode=403`)

개발/CI에서는 실제 vLLM 없이도 `tests/test_onprem_poc_http_smoke.py`가 local OpenAI-compatible mock HTTP server를 띄워 같은 provider wire protocol과 smoke scenario를 검증한다. 이 테스트는 운영 모델 품질이나 latency를 증명하지 않고, 환경변수 전환, HTTP request/response shape, streaming TTFT 수집 경로, embedding dimension guard, 기존 MeetingMind service 함수 재사용 경로가 깨지지 않는지만 확인한다. 실제 Day 2/3 완료 판단은 위 `RUN_ONPREM_AI_POC_SMOKE=true` 명령을 vLLM/TGI/NIM 등 실제 local provider에 연결해 측정한 결과를 기준으로 한다.

실제 PostgreSQL/pgvector와 local-compatible provider wire path를 함께 검증하려면 빈 평가 DB에 Backend migration을 적용한 뒤 아래 opt-in test를 실행한다. 평가 DB는 기존 운영/개발 DB와 분리한다. 로컬 compose DB를 사용할 때는 helper가 DB를 만들고 `backend/src/main/resources/db/migration/V*.sql`을 Flyway 버전 순서로 적용한다. host에 `psql`이 없으면 실행 중인 `meetingmind-postgres-local` 컨테이너의 `psql`을 사용한다. 컨테이너 이름과 DB user는 `ONPREM_POC_POSTGRES_CONTAINER`, `ONPREM_POC_POSTGRES_USER`로 바꿀 수 있다. 이미 존재하는 평가 DB를 다시 만들 때만 `ONPREM_POC_RESET_EVAL_DATABASE=true`를 명시한다.

```bash
ONPREM_POC_ADMIN_DATABASE_URL=postgresql://meetingmind:meetingmind_local@localhost:5434/postgres \
ONPREM_POC_EVAL_DATABASE_NAME=meetingmind_onprem_eval \
./onprem_poc_prepare_eval_db.sh
```

이 테스트는 mock OpenAI-compatible HTTP endpoint를 호출하지만, 색인/worker/generation/swap/retrieval/scope filter는 실제 `AI_TEST_DATABASE_URL`의 기존 schema와 repository를 사용한다. 또한 9개 smoke scenario를 실행하고 실제 DB retrieval probe가 포함된 결과를 `onprem_poc_validate.py`로 판정한다.

```bash
RUN_ONPREM_POC_POSTGRES_INTEGRATION=true \
AI_TEST_DATABASE_URL=postgresql://meetingmind:meetingmind_local@localhost:5434/meetingmind_onprem_eval \
./.venv/bin/python -m unittest tests.test_onprem_poc_postgres_integration
```

## Embedding worker

로컬 PostgreSQL에 V12 migration이 적용된 후 아래 명령으로 실행합니다.

```bash
python -m app.embedding_worker
```

Docker에서는 루트에서 `docker compose -f compose.local.yml --profile ai up --build meetingmind-ai meetingmind-ai-worker`를 사용합니다. `meetingmind-ai`와 `meetingmind-ai-worker`는 같은 `AI_DATABASE_URL`, `AI_EMBEDDING_PROVIDER`, `AI_VECTOR_DIMENSION` 계열 환경변수를 받으며, FastAPI 서버는 `AI_TEXT_PROVIDER` 계열 환경변수도 함께 받는다.

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
