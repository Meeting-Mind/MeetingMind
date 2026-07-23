import json
import os
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

import onprem_poc_smoke
import onprem_poc_validate


class OpenAICompatibleMockHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length).decode("utf-8"))
        if self.path == "/v1/chat/completions":
            self.handle_chat_completions(body)
            return
        if self.path == "/v1/embeddings":
            self.handle_embeddings(body)
            return
        self.send_response(404)
        self.end_headers()

    def handle_chat_completions(self, body):
        content = response_content_for(body)
        if body.get("stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            midpoint = max(1, len(content) // 2)
            chunks = (content[:midpoint], content[midpoint:])
            for chunk in chunks:
                payload = {"model": body.get("model"), "choices": [{"delta": {"content": chunk}}]}
                self.wfile.write(f"data: {json.dumps(payload, ensure_ascii=False)}\n\n".encode("utf-8"))
            usage = {"model": body.get("model"), "choices": [], "usage": {"prompt_tokens": 20, "completion_tokens": 8}}
            self.wfile.write(f"data: {json.dumps(usage, ensure_ascii=False)}\n\n".encode("utf-8"))
            self.wfile.write(b"data: [DONE]\n\n")
            return

        payload = {
            "model": body.get("model"),
            "choices": [{"message": {"content": content}}],
            "usage": {"prompt_tokens": 20, "completion_tokens": 8},
        }
        self.send_json(payload)

    def handle_embeddings(self, body):
        inputs = body.get("input") or []
        dimension = int(body.get("dimensions") or 1536)
        payload = {
            "model": body.get("model"),
            "data": [
                {"index": index, "embedding": [0.01] * dimension}
                for index, _text in enumerate(inputs)
            ]
        }
        self.send_json(payload)

    def send_json(self, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, _format, *_args):
        return


def response_content_for(body):
    messages = body.get("messages") or []
    combined = "\n".join(str(message.get("content", "")) for message in messages if isinstance(message, dict))
    if "meetingmind_onprem_smoke" in json.dumps(body, ensure_ascii=False):
        return '{"supported":true,"answer":"ok","sourceIds":["smoke-source"]}'
    if "회의 보고서 생성" in combined:
        return (
            '{"supported":true,"summary":"출시일과 QA 마감이 확정됐습니다.",'
            '"decisions":[{"title":"출시일 확정","rationale":null,"sourceIds":["decision-001"]}],'
            '"actionItems":[{"title":"모바일 앱 QA 완료","assignee":"민지","dueDate":"2026-09-12",'
            '"sourceIds":["action-001"],"confirmationState":"candidate"}],'
            '"markdown":"## 요약\\n출시일과 QA 마감이 확정됐습니다."}'
        )
    if "태스크 후보 추출" in combined:
        return (
            '{"supported":true,"tasks":[{"title":"모바일 앱 QA 완료","assignee":"민지",'
            '"dueDate":"2026-09-12","sourceIds":["action-001"],"confirmationState":"candidate"}]}'
        )
    if "오로라 프로젝트 출시 목표" in combined:
        return '{"supported":true,"answer":"출시일과 QA 마감이 정해졌습니다.","sourceIds":["meeting-summary-001"]}'
    if "해외 지사 예산" in combined:
        return '{"supported":false,"answer":"","sourceIds":[]}'
    if "모바일 앱 QA 완료" in combined:
        return '{"supported":true,"answer":"모바일 앱 QA는 2026년 9월 12일까지 완료합니다.","sourceIds":["action-001"]}'
    return '{"supported":true,"answer":"출시일은 2026년 9월 18일이고 QA는 9월 12일까지입니다.","sourceIds":["segment-001"]}'


class OnPremPocHttpSmokeTest(unittest.TestCase):
    def test_smoke_runner_exercises_local_openai_compatible_http_text_and_embedding(self):
        try:
            server = ThreadingHTTPServer(("127.0.0.1", 0), OpenAICompatibleMockHandler)
        except PermissionError as error:
            raise unittest.SkipTest("local socket binding is not permitted in this sandbox") from error
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{server.server_port}/v1"
        started_at = time.perf_counter()
        started_wall_time = onprem_poc_smoke.utc_now_iso()
        try:
            with patch.dict(
                os.environ,
                {
                    "RUN_ONPREM_AI_POC_SMOKE": "true",
                    "AI_TEXT_PROVIDER": "local-openai-compatible",
                    "AI_TEXT_BASE_URL": base_url,
                    "AI_TEXT_API_KEY": "local-token",
                    "AI_TEXT_MODEL": "mock-local-llm",
                    "AI_TEXT_API_STYLE": "chat-completions",
                    "AI_TEXT_STREAM": "true",
                    "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                    "AI_EMBEDDING_BASE_URL": base_url,
                    "AI_EMBEDDING_API_KEY": "local-token",
                    "AI_EMBEDDING_MODEL": "mock-local-embedding",
                    "AI_EMBEDDING_DIMENSION": "1536",
                    "AI_VECTOR_DIMENSION": "1536",
                    "AI_DATABASE_URL": "postgresql://meetingmind-smoke",
                    "AI_INTERNAL_SERVICE_TOKEN": "smoke-service-token",
                    "ONPREM_POC_PROJECT_ID": "smoke-project",
                    "ONPREM_POC_ALLOWED_MEETING_IDS": "smoke-meeting",
                    "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
                },
                clear=False,
            ), patch("onprem_poc_smoke.search_postgres_sources", return_value=[object(), object()]):
                metrics = [
                    onprem_poc_smoke.run_provider_probe(),
                    onprem_poc_smoke.run_embedding_probe(),
                    onprem_poc_smoke.run_retrieval_latency_probe(),
                    onprem_poc_smoke.run_scenario("meeting_ai", onprem_poc_smoke.meeting_ai_scenario),
                    onprem_poc_smoke.run_scenario("project_ai", onprem_poc_smoke.project_ai_scenario),
                    onprem_poc_smoke.run_scenario("report", onprem_poc_smoke.report_scenario),
                    onprem_poc_smoke.run_scenario("task", onprem_poc_smoke.task_scenario),
                    onprem_poc_smoke.run_expected_unsupported(
                        "meeting_ai_unsupported",
                        onprem_poc_smoke.meeting_ai_unsupported_scenario,
                    ),
                    onprem_poc_smoke.run_permission_guard(),
                ]
                summary = onprem_poc_smoke.summarize(metrics)
                result = {
                    "run": onprem_poc_smoke.asdict(
                        onprem_poc_smoke.smoke_run(started_at, started_wall_time, preflight_only=False)
                    ),
                    "config": onprem_poc_smoke.asdict(onprem_poc_smoke.smoke_config()),
                    "summary": onprem_poc_smoke.asdict(summary),
                    "metrics": [onprem_poc_smoke.asdict(metric) for metric in metrics],
                }
        finally:
            server.shutdown()
            server.server_close()

        self.assertTrue(all(metric.ok for metric in metrics), metrics)
        self.assertTrue(summary.ok)
        self.assertEqual(summary.citationSuccessRate, 1.0)
        self.assertEqual(summary.jsonParsingSuccessRate, 1.0)
        self.assertTrue(summary.unsupportedGuardPassed)
        self.assertTrue(summary.permissionGuardPassed)
        self.assertTrue(summary.retrievalLatencyMeasured)
        self.assertTrue(summary.retrievalRequirementPassed)
        self.assertEqual({metric.scenario for metric in metrics}, {
            "text_provider_probe",
            "embedding_provider_probe",
            "retrieval_latency_probe",
            "meeting_ai",
            "project_ai",
            "report",
            "task",
            "meeting_ai_unsupported",
            "project_ai_permission_guard",
        })
        embedding = next(metric for metric in metrics if metric.scenario == "embedding_provider_probe")
        self.assertEqual(embedding.provider, "local-openai-compatible")
        self.assertEqual(embedding.itemCount, 1536)
        self.assertTrue(embedding.modelObserved)
        self.assertEqual(embedding.model, "mock-local-embedding")
        meeting = next(metric for metric in metrics if metric.scenario == "meeting_ai")
        self.assertEqual(meeting.provider, "local-openai-compatible")
        self.assertEqual(meeting.apiStyle, "chat-completions")
        self.assertEqual(meeting.responseFormatMode, "json_schema")
        self.assertTrue(meeting.modelObserved)
        self.assertTrue(meeting.stream)
        self.assertIsNotNone(meeting.ttftMs)
        self.assertIsNotNone(meeting.tokensPerSecond)
        self.assertEqual(onprem_poc_validate.validate_result(result), [])


if __name__ == "__main__":
    unittest.main()
