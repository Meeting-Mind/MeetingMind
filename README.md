# MeetingMind

MeetingMind는 회의 기록을 프로젝트 지식 자산으로 전환하는 AI 협업 플랫폼입니다.
현재 저장소는 프로토타입 단계이며, 세 영역으로 나뉩니다.

- `frontend`: React + Vite + TypeScript
- `backend`: Spring Boot 3 + Java 21 + Gradle
- `ai`: FastAPI 기반 Meeting AI 응답 서비스

## 구조

```text
frontend/
backend/
ai/
requirements/
specs/
README.md
```

제품/구현 기준은 `AGENTS.md`, `.specify/memory/constitution.md`, `requirements/INDEX.md`, `specs/001-meetingmind-core/*`를 따릅니다.

## 실행

### 1. 로컬 데이터베이스

다른 프로젝트의 PostgreSQL과 분리된 PostgreSQL 16 + pgvector를 host `5434`에서 실행합니다.

```bash
docker compose -f compose.local.yml up -d meetingmind-db
```

기본 로컬 접속값은 `meetingmind/meetingmind_local`, DB 이름은 `meetingmind`입니다. 필요하면 `MEETINGMIND_DB_PORT`, `MEETINGMIND_DB_NAME`, `MEETINGMIND_DB_USER`, `MEETINGMIND_DB_PASSWORD`로 덮어씁니다.

Backend는 기본 profile이 `local`로 설정되어 있어 별도 profile 지정 없이도 위 Compose 기본 접속값을 사용하고 시작 시 Flyway migration을 적용합니다.

```bash
cd backend
./gradlew bootRun
```

명시적으로 실행할 때는 `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`도 동일합니다.

`MEETINGMIND_DB_PORT`, `MEETINGMIND_DB_NAME`, `MEETINGMIND_DB_USER`, `MEETINGMIND_DB_PASSWORD`를 export하면 Compose와 `local` profile에 같은 값이 적용됩니다. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`로 Backend 접속값만 별도 override할 수도 있습니다. 배포/CI의 `db` profile은 Spring datasource 환경변수 세 개를 필수로 사용합니다.

로컬 DB를 중지할 때는 다음 명령을 사용합니다. volume 삭제는 schema를 처음부터 다시 검증할 때만 명시적으로 수행합니다.

```bash
docker compose -f compose.local.yml stop meetingmind-db
```

### 2. 백엔드

```bash
cd backend
./gradlew bootRun
```

기본 포트는 `8080`입니다. 현재 주요 API는 `GET /api/workspace`, `POST /api/livekit/token`, `POST /api/v1/auth/*`입니다.

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

기본 포트는 `5173`입니다.

필요하면 `.env`에 아래 값을 둘 수 있습니다.

```bash
VITE_API_BASE_URL=http://localhost:8080
```

### 4. AI 서비스

```bash
cd ai
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

필요한 주요 환경변수는 `ai/.env.example`을 기준으로 설정합니다.

## 검증

Backend 테스트는 Gradle이 `test` profile을 적용하므로 로컬 DB 실행 여부와 독립적으로 동작합니다. 실제 PostgreSQL schema는 Backend 기본 실행 시 Flyway가 검증합니다.

```bash
cd frontend && npm run build
cd backend && ./gradlew test
cd ai && python3 -m compileall app tests
```

## 현재 상태

- Frontend는 랜딩, 워크스페이스, 라이브 미팅, 프로젝트 개요, Meeting AI, Report Agent 화면을 제공합니다.
- Backend는 workspace 데모 API, LiveKit 토큰 발급, prototype auth API를 제공합니다.
- AI 서버는 Meeting AI 질문 응답, 용어 설명, RAG 기반 prototype API를 제공합니다.
- Frontend는 backend API 실패 시 데모용 mock fallback으로 동작할 수 있습니다.

## 다음 작업 추천

- `requirements/INDEX.md`에서 작업별 요구사항 문서를 확인합니다.
- `specs/001-meetingmind-core/tasks.md`의 미완료 task 기준으로 구현 범위를 잡습니다.
- mock API와 실제 영속 데이터/API 경계를 단계적으로 분리합니다.
