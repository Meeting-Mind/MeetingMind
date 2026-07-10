import json
import unittest
from unittest.mock import patch

from app.main import (
    AiSource,
    ExplainTermRequest,
    ExtractTasksRequest,
    GlossaryItem,
    MeetingAiChatRequest,
    TranscriptRow,
    ai_observability_fields,
    explain_term,
    extract_tasks,
    meeting_ai_chat,
    meeting_chat,
    parse_report_response,
    parse_task_candidates_response,
)
from app.rag import InMemoryRagRetriever, RagChunk, RagSearchRequest, chunk_to_source


class ExplainTermTest(unittest.TestCase):
    def test_glossary_definition_takes_priority_without_external_call(self):
        payload = ExplainTermRequest(
            term="PGVECTOR",
            glossary=[
                GlossaryItem(
                    term="pgvector",
                    definition="PostgreSQL에서 vector embedding을 저장하고 검색하는 확장입니다.",
                    sourceId="glossary-pgvector",
                )
            ],
            transcript=[
                TranscriptRow(time="06:10:03", speaker="김진수", text="pgvector로 RAG 검색을 구성합니다.")
            ],
        )

        with patch("app.main.call_openai_text") as call_openai_text:
            response = explain_term(payload)

        call_openai_text.assert_not_called()
        self.assertEqual(response.sourceType, "glossary")
        self.assertEqual(response.model, "local-glossary")
        self.assertFalse(response.unsupported)
        self.assertEqual(response.sources[0].sourceId, "glossary-pgvector")

    def test_returns_unsupported_when_context_has_no_evidence(self):
        payload = ExplainTermRequest(term="권한 필터")

        response = explain_term(payload)

        self.assertTrue(response.unsupported)
        self.assertEqual(response.sourceType, "none")
        self.assertEqual(response.sources, [])
        self.assertIn("확인할 수 없습니다", response.explanation)

    def test_transcript_evidence_uses_rag_window_and_external_call_is_mocked(self):
        payload = ExplainTermRequest(
            term="RAG",
            transcript=[
                TranscriptRow(time="06:10:01", speaker="A", text="RAG 후보 chunk를 만들겠습니다."),
                TranscriptRow(time="06:10:02", speaker="B", text="RAG는 회의별 범위로 제한합니다."),
                TranscriptRow(time="06:10:03", speaker="C", text="프로젝트 RAG에는 공식 지식을 섞습니다."),
                TranscriptRow(time="06:10:04", speaker="D", text="RAG source metadata가 필요합니다."),
                TranscriptRow(time="06:10:05", speaker="E", text="RAG 외 질문은 확인 불가입니다."),
            ],
        )

        with patch("app.main.call_openai_text", return_value=("회의 맥락에서 검색 범위를 뜻합니다.", "test-model")):
            response = explain_term(payload)

        self.assertFalse(response.unsupported)
        self.assertEqual(response.sourceType, "transcript")
        self.assertEqual(response.model, "test-model")
        self.assertEqual(len(response.sources), 1)
        self.assertEqual(response.sources[0].sourceId, "segment-001")
        self.assertIn("RAG 후보 chunk", response.sources[0].text)
        self.assertIn("RAG 외 질문", response.sources[0].text)


class RagMappingTest(unittest.TestCase):
    def test_chunk_to_source_preserves_source_metadata(self):
        chunk = RagChunk(
            chunkId="meeting-001:transcript:0001",
            scope="meeting",
            projectId="space-001",
            meetingId="meeting-001",
            sourceType="transcript",
            sourceId="segment-window-001",
            sourceSegmentIds=("segment-001", "segment-002"),
            title="API 구조 논의",
            speakerNames=("김진수", "이미주"),
            startMs=370300,
            endMs=374100,
            content="김진수: ERD 구조를 수정해야 합니다.",
            embeddingText="회의: API 구조 논의\n내용: ERD 구조를 수정해야 합니다.",
        )

        source = chunk_to_source(chunk)

        self.assertEqual(source.sourceId, "segment-window-001")
        self.assertEqual(source.type, "transcript")
        self.assertEqual(source.title, "API 구조 논의")
        self.assertEqual(source.speaker, "김진수, 이미주")
        self.assertEqual(source.startMs, 370300)
        self.assertEqual(source.endMs, 374100)
        self.assertEqual(source.text, "김진수: ERD 구조를 수정해야 합니다.")


class RagSafetyTest(unittest.TestCase):
    def test_meeting_scope_excludes_other_meetings_and_project_knowledge(self):
        chunks = [
            RagChunk(
                chunkId="meeting-001:transcript:0001",
                scope="meeting",
                projectId="space-001",
                meetingId="meeting-001",
                sourceType="transcript",
                sourceId="segment-001",
                content="김진수: 권한 필터를 먼저 적용합니다.",
                embeddingText="회의: 회의1\n범위: meeting\n출처: transcript\n내용: 권한 필터",
            ),
            RagChunk(
                chunkId="meeting-002:transcript:0001",
                scope="meeting",
                projectId="space-001",
                meetingId="meeting-002",
                sourceType="transcript",
                sourceId="segment-999",
                content="이미주: 권한 필터 구현은 다른 회의에서 논의했습니다.",
                embeddingText="회의: 회의2\n범위: meeting\n출처: transcript\n내용: 권한 필터",
            ),
            RagChunk(
                chunkId="space-001:projectKnowledge:0001",
                scope="project",
                projectId="space-001",
                meetingId=None,
                sourceType="projectKnowledge",
                sourceId="knowledge-001",
                content="프로젝트 공식 권한 정책입니다.",
                embeddingText="회의: 공식 지식\n범위: project\n출처: projectKnowledge\n내용: 권한 정책",
            ),
        ]

        results = InMemoryRagRetriever(chunks).search(
            RagSearchRequest(
                query="권한 필터",
                scope="meeting",
                projectId="space-001",
                meetingId="meeting-001",
                limit=10,
            )
        )

        self.assertEqual([result.chunk.sourceId for result in results], ["segment-001"])

    def test_project_scope_excludes_disallowed_meeting_chunks_but_keeps_official_knowledge(self):
        chunks = [
            RagChunk(
                chunkId="space-001:projectKnowledge:0001",
                scope="project",
                projectId="space-001",
                sourceType="projectKnowledge",
                sourceId="knowledge-001",
                content="공식 권한 정책은 Project Knowledge에 저장합니다.",
                embeddingText="회의: 공식 지식\n범위: project\n출처: projectKnowledge\n내용: 권한 정책",
            ),
            RagChunk(
                chunkId="meeting-allowed:meetingSummary:0001",
                scope="project",
                projectId="space-001",
                meetingId="meeting-allowed",
                sourceType="meetingSummary",
                sourceId="meeting-summary-allowed",
                content="접근 가능한 회의에서 권한 필터가 논의되었습니다.",
                embeddingText="회의: 접근 가능\n범위: project\n출처: meetingSummary\n내용: 권한 필터",
            ),
            RagChunk(
                chunkId="meeting-denied:meetingSummary:0001",
                scope="project",
                projectId="space-001",
                meetingId="meeting-denied",
                sourceType="meetingSummary",
                sourceId="meeting-summary-denied",
                content="접근 불가 회의의 권한 필터 논의입니다.",
                embeddingText="회의: 접근 불가\n범위: project\n출처: meetingSummary\n내용: 권한 필터",
            ),
        ]

        results = InMemoryRagRetriever(chunks).search(
            RagSearchRequest(
                query="권한 필터",
                scope="project",
                projectId="space-001",
                allowedMeetingIds=("meeting-allowed",),
                limit=10,
            )
        )

        source_ids = [result.chunk.sourceId for result in results]
        self.assertIn("knowledge-001", source_ids)
        self.assertIn("meeting-summary-allowed", source_ids)
        self.assertNotIn("meeting-summary-denied", source_ids)

    def test_meeting_chat_does_not_call_llm_without_sources(self):
        payload = MeetingAiChatRequest(
            meetingId="meeting-001",
            question="예산 승인 내역은?",
            transcript=[
                TranscriptRow(time="00:01:00", speaker="김진수", text="권한 필터를 먼저 적용합니다.")
            ],
        )

        with patch("app.main.call_openai_text") as call_openai_text:
            response = meeting_chat(payload)

        call_openai_text.assert_not_called()
        self.assertTrue(response.unsupported)
        self.assertEqual(response.sources, [])
        self.assertEqual(response.model, "context-only")

    def test_task_extraction_does_not_call_llm_without_sources(self):
        payload = ExtractTasksRequest(meetingId="meeting-001", title="주간 회의")

        with patch("app.main.call_openai_text") as call_openai_text:
            response = extract_tasks(payload)

        call_openai_text.assert_not_called()
        self.assertTrue(response.unsupported)
        self.assertEqual(response.tasks, [])
        self.assertEqual(response.sources, [])

    def test_generated_source_ids_are_filtered_to_provided_sources(self):
        sources = [
            AiSource(
                sourceId="segment-001",
                type="transcript",
                title="주간 회의",
                text="김진수: ERD 수정안을 문서화하겠습니다.",
            )
        ]

        report = parse_report_response(
            '{"summary":"요약","decisions":[{"title":"결정","sourceIds":["segment-001","forged-source"]}],'
            '"actionItems":[{"title":"ERD 수정안 문서화","sourceIds":["forged-source"],'
            '"confirmationState":"confirmed"}],"markdown":"## 요약"}',
            model="test-model",
            sources=sources,
        )
        tasks = parse_task_candidates_response(
            '{"tasks":[{"title":"ERD 수정안 문서화","sourceIds":["segment-001","forged-source"],'
            '"confirmationState":"confirmed"}]}',
            model="test-model",
            sources=sources,
        )

        self.assertEqual(report.decisions[0].sourceIds, ["segment-001"])
        self.assertEqual(report.actionItems[0].sourceIds, [])
        self.assertEqual(report.actionItems[0].confirmationState, "candidate")
        self.assertEqual(tasks.tasks[0].sourceIds, ["segment-001"])
        self.assertEqual(tasks.tasks[0].confirmationState, "candidate")


class AiObservabilityTest(unittest.TestCase):
    def test_endpoint_logs_model_source_count_and_unsupported_reason(self):
        payload = MeetingAiChatRequest(
            meetingId="meeting-001",
            question="민감한 질문 원문",
            transcript=[
                TranscriptRow(time="00:01:00", speaker="김진수", text="권한 필터를 먼저 적용합니다.")
            ],
        )

        with self.assertLogs("meetingmind.ai", level="INFO") as logs:
            response = meeting_ai_chat(payload)

        self.assertTrue(response.unsupported)
        log_message = logs.output[0]
        self.assertIn("ai_request_completed", log_message)
        self.assertNotIn("민감한 질문 원문", log_message)

        payload_text = log_message.split("ai_request_completed ", 1)[1]
        fields = json.loads(payload_text)
        self.assertEqual(fields["endpoint"], "meeting-ai.chat")
        self.assertEqual(fields["model"], "context-only")
        self.assertEqual(fields["sourceCount"], 0)
        self.assertTrue(fields["unsupported"])
        self.assertEqual(fields["unsupportedReason"], "NO_SOURCES")
        self.assertIsInstance(fields["durationMs"], int)

    def test_observability_fields_count_sources_for_supported_response(self):
        response = parse_task_candidates_response(
            '{"tasks":[{"title":"ERD 수정","sourceIds":["segment-001"]}]}',
            model="test-model",
            sources=[
                AiSource(
                    sourceId="segment-001",
                    type="transcript",
                    title="주간 회의",
                    text="ERD 수정 작업을 진행합니다.",
                )
            ],
        )

        fields = ai_observability_fields("meeting-ai.extract-tasks", response, 12)

        self.assertEqual(fields["endpoint"], "meeting-ai.extract-tasks")
        self.assertEqual(fields["durationMs"], 12)
        self.assertEqual(fields["model"], "test-model")
        self.assertEqual(fields["sourceCount"], 1)
        self.assertFalse(fields["unsupported"])
        self.assertIsNone(fields["unsupportedReason"])


if __name__ == "__main__":
    unittest.main()
