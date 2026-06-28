# MeetingMind

기존 정적 HTML 와이어프레임을 다음 구조로 재구성했습니다.

- `frontend`: React + Vite + TypeScript
- `backend`: Spring Boot 3 + Maven

## 구조

```text
frontend/
backend/
README.md
```

실제 앱은 `frontend`와 `backend`에서 실행합니다.

## 실행

### 1. 백엔드

```bash
cd /Users/miju/final/backend
mvn spring-boot:run
```

기본 포트는 `8080`이고 `GET /api/workspace`를 제공합니다.

### 2. 프론트엔드

```bash
cd /Users/miju/final/frontend
npm install
npm run dev
```

기본 포트는 `5173`입니다.

필요하면 `.env`에 아래 값을 둘 수 있습니다.

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## 현재 상태

- 랜딩, 라이브 미팅, 프로젝트 개요, Meeting AI, 보고서 Agent 페이지를 React 라우트로 이동
- Spring Boot에서 화면용 더미 데이터 API 제공
- 백엔드가 없어도 프론트가 뜨도록 fallback mock data 포함

## 다음 작업 추천

- `WorkspaceController`의 더미 데이터를 DB/실제 서비스 계층으로 분리
- React 페이지를 공통 레이아웃과 재사용 컴포넌트로 정리
- Vite dev server proxy 또는 배포용 reverse proxy 설정 추가
