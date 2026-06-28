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

`ai/.env`가 없으면 `../backend/.env`도 fallback으로 읽습니다.
