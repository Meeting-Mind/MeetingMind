FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN groupadd --system meetingmind \
    && useradd --system --gid meetingmind --home-dir /app meetingmind
WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt
COPY --chown=meetingmind:meetingmind app app
COPY --chown=meetingmind:meetingmind onprem_poc_smoke.py onprem_poc_validate.py onprem_poc_run.sh onprem_poc_prepare_eval_db.sh ./
RUN chown meetingmind:meetingmind /app && chmod +x /app/onprem_poc_run.sh /app/onprem_poc_prepare_eval_db.sh

USER meetingmind
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
