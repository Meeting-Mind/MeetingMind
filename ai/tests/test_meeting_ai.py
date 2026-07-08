import unittest
from unittest.mock import patch

from app.main import ExplainTermRequest, GlossaryItem, TranscriptRow, explain_term
from app.rag import RagChunk, chunk_to_source


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


if __name__ == "__main__":
    unittest.main()
