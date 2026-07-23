# MeetingMind

MeetingMind는 회의 기록을 프로젝트 지식 자산으로 전환하는 AI 협업 플랫폼입니다.
현재 저장소는 프로토타입 단계이며, 다섯 영역으로 나뉩니다.

- `frontend`: React + Vite + TypeScript
- `bff`: Spring Boot 3 Web BFF + Spring Session Redis
- `auth`: Spring Boot 3 Auth Service + 전용 PostgreSQL
- `backend`: Spring Boot 3 + Java 21 + Gradle
- `ai`: FastAPI 기반 Meeting AI 응답 서비스

## 구조

```text
frontend/
bff/
auth/
backend/
ai/
requirements/
specs/
README.md
```

제품/구현 기준은 `AGENTS.md`, `.specify/memory/constitution.md`, `requirements/INDEX.md`, `specs/001-meetingmind-core/*`를 따릅니다.

## 로컬 환경변수 기준

환경변수는 서비스별 파일이 아니라 값의 소유권으로 나눕니다. 실제 `.env` 파일은 모두 Git에서 제외합니다.

| 위치 | 소유 값 | 읽는 서비스 |
| --- | --- | --- |
| 루트 `.env` | Compose DB/Redis, `AI_INTERNAL_SERVICE_TOKEN`, `OPENAI_API_KEY`, AI provider/model | Compose AI, Backend AI gateway, AI worker |
| `backend/.env` | LiveKit, ngrok callback, STT provider, Backend Google/JWT | Backend |
| `bff/.env` | Token Vault key, BFF/Auth/Core endpoint | BFF |
| `frontend/.env` | `VITE_*` 공개 설정만 | Frontend |
| `auth/.env` | Auth DB, refresh hash, KMS/Auth 설정 | Auth |
| `ai/.env` | Compose 없이 AI만 직접 실행할 때의 provider 설정 | AI |

처음에는 예시 파일을 복사합니다.

```bash
cp .env.example .env
cp backend/.env.example backend/.env
cp bff/.env.example bff/.env
cp frontend/.env.example frontend/.env
```

`AI_INTERNAL_SERVICE_TOKEN`은 루트 `.env` 한 곳에서만 설정합니다. Backend는 시작 시 루트 `.env`를 자동으로 읽고, `backend/.env`는 Backend 전용 값을 덮어쓸 때만 사용합니다. AI Compose는 토큰이 없으면 시작하지 않아 Backend-AI 인증 불일치를 조기에 차단합니다.

OpenAI 키의 표준 이름은 `OPENAI_API_KEY`입니다. 기존 `OPEN_AI_KEY`는 로컬 호환을 위해 일시 지원하지만 새 설정에는 사용하지 않습니다.

## 실행

### 1. 로컬 데이터베이스와 세션 저장소

다른 프로젝트의 PostgreSQL과 분리된 PostgreSQL 16 + pgvector를 host `5434`에서 실행합니다.

```bash
docker compose -f compose.local.yml up -d meetingmind-db meetingmind-redis
```

기본 로컬 접속값은 `meetingmind/meetingmind_local`, DB 이름은 `meetingmind`입니다. 필요하면 `MEETINGMIND_DB_PORT`, `MEETINGMIND_DB_NAME`, `MEETINGMIND_DB_USER`, `MEETINGMIND_DB_PASSWORD`로 덮어씁니다.

Backend는 기본 profile이 `local`로 설정되어 있어 별도 profile 지정 없이도 위 Compose 기본 접속값을 사용하고 시작 시 Flyway migration을 적용합니다.

```bash
cd backend
./gradlew bootRun
```

명시적으로 실행할 때는 `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`도 동일합니다.

`MEETINGMIND_DB_PORT`, `MEETINGMIND_DB_NAME`, `MEETINGMIND_DB_USER`, `MEETINGMIND_DB_PASSWORD`를 export하면 Compose와 `local` profile에 같은 값이 적용됩니다. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`로 Backend 접속값만 별도 override할 수도 있습니다. 배포/CI의 `db` profile은 Spring datasource 환경변수 세 개를 필수로 사용합니다.

로컬 DB와 Redis를 중지할 때는 다음 명령을 사용합니다. volume 삭제는 schema를 처음부터 다시 검증할 때만 명시적으로 수행합니다.

```bash
docker compose -f compose.local.yml stop meetingmind-db meetingmind-redis
```

### 2. 백엔드

```bash
cd backend
./gradlew bootRun
```

기본 포트는 `8080`입니다. 현재 주요 API는 `GET /api/workspace`, `POST /api/livekit/token`, `POST /api/v1/auth/*`입니다.

### 3. Web BFF

브라우저는 Backend를 직접 호출하지 않고 BFF의 opaque session cookie와 CSRF를 사용합니다. 로컬 Token Vault key는 Git에 포함되지 않는 로컬 secret으로 고정해 사용해야 합니다.

```bash
cd bff
set -a
source .env
set +a
./gradlew bootRun
```

기본 포트는 `8081`입니다. 기존에 발급한 로컬 세션을 유지하려면 매 실행마다 새 key를 생성하지 말고 같은 값을 안전한 로컬 환경변수로 재사용합니다.

### 4. Auth Service

Auth Service는 Core PostgreSQL과 분리된 전용 DB와 runtime/migration 계정을 사용합니다.

```bash
docker compose -f compose.local.yml --profile auth up -d meetingmind-auth-db
cd auth
./gradlew bootRun
```

기본 포트는 `8082`, Auth PostgreSQL host 포트는 `5435`입니다. T032는 internal local/Google 인증, refresh rotation, revoke/revoke-all과 transactional outbox producer를 제공하고 T033은 AWS KMS `RS256` access signing과 내부 JWKS를 제공합니다. KMS key ring을 설정하지 않은 로컬 기본값은 token 발급을 `503 TOKEN_ISSUER_UNAVAILABLE`로 fail closed합니다. 운영 key ring과 교체 순서는 `auth/README.md`를 따릅니다.

### 5. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

기본 포트는 `5173`입니다.

개발 서버는 `/api`를 기본적으로 `http://127.0.0.1:8081` BFF로 proxy합니다. BFF 포트를 바꾼 경우에만 `.env`에서 proxy 대상을 바꿉니다.

```bash
VITE_BFF_PROXY_TARGET=http://127.0.0.1:8081
```

`VITE_API_BASE_URL`로 Backend를 직접 지정하는 방식은 지원하지 않습니다.

### 6. AI 서비스

```bash
cd ai
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

필요한 주요 환경변수는 `ai/.env.example`을 기준으로 설정합니다.

Docker Compose로 FastAPI AI 서버와 embedding worker를 함께 띄울 때는 `ai` profile을 사용합니다. 기본 provider는 OpenAI이며, 온프레 PoC에서는 같은 환경변수를 `local-openai-compatible`로 바꿔 전달합니다.
최종 PoC용 전체 env 목록은 `ai/onprem.env.example`을 기준으로 채웁니다.

```bash
AI_TEXT_PROVIDER=local-openai-compatible \
AI_TEXT_BASE_URL=http://host.docker.internal:8001/v1 \
AI_TEXT_MODEL=qwen2.5-14b-instruct \
AI_TEXT_API_STYLE=chat-completions \
AI_TEXT_STREAM=true \
AI_EMBEDDING_PROVIDER=local-openai-compatible \
AI_EMBEDDING_BASE_URL=http://host.docker.internal:8002/v1 \
AI_EMBEDDING_MODEL=bge-m3 \
AI_EMBEDDING_DIMENSION=1536 \
AI_VECTOR_DIMENSION=1536 \
docker compose -f compose.local.yml --profile ai up --build meetingmind-ai meetingmind-ai-worker
```

`meetingmind-ai` 컨테이너는 `/health` healthcheck를 사용합니다. `text_provider`, `embedding_provider`, local endpoint configured 및 local-compatible 판정, embedding/vector dimension 일치 여부, DB/token configured 여부만 노출하며 secret, base URL, DSN 원문은 노출하지 않습니다.

Spring Backend는 기존 AI Gateway client와 internal API 계약을 그대로 사용합니다. 루트 `.env`의 `AI_INTERNAL_SERVICE_TOKEN`을 자동으로 읽으므로 로컬 Backend와 Compose AI가 같은 값을 사용합니다. 기본 AI URL은 `http://localhost:8000`입니다.

```bash
cd backend
./gradlew bootRun
```

컨테이너 네트워크 안에서 Backend를 실행하는 환경에서는 `MEETINGMIND_AI_BASE_URL=http://meetingmind-ai:8000`을 사용합니다.

## 검증

Backend 테스트는 Gradle이 `test` profile을 적용하므로 로컬 DB 실행 여부와 독립적으로 동작합니다. 실제 PostgreSQL schema는 Backend 기본 실행 시 Flyway가 검증합니다.

```bash
cd frontend && npm run build
cd bff && BFF_REDIS_INTEGRATION=true BFF_REDIS_PORT=6380 ./gradlew test bootJar
cd auth && ./gradlew test bootJar
cd backend && ./gradlew test
cd ai && python3 -m compileall app tests
```

## 현재 상태

- Frontend는 랜딩, 워크스페이스, 라이브 미팅, 프로젝트 개요, Meeting AI, Report Agent 화면을 제공합니다.
- Web BFF는 Redis session, CSRF, 암호화 Token Vault, 자동 refresh와 업무 API allowlist proxy를 제공합니다.
- Auth Service는 전용 PostgreSQL/Flyway schema, DML-only runtime 계정, health probe, 인증/session/revoke runtime과 KMS RS256 access signing/내부 JWKS를 제공합니다.
- Backend는 workspace 데모 API, LiveKit 토큰 발급, prototype auth API를 제공합니다.
- AI 서버는 Meeting AI 질문 응답, 용어 설명, RAG 기반 prototype API를 제공합니다.
- Frontend는 backend API 실패 시 데모용 mock fallback으로 동작할 수 있습니다.

## 다음 작업 추천

- `requirements/INDEX.md`에서 작업별 요구사항 문서를 확인합니다.
- `specs/001-meetingmind-core/tasks.md`의 미완료 task 기준으로 구현 범위를 잡습니다.
- mock API와 실제 영속 데이터/API 경계를 단계적으로 분리합니다.
