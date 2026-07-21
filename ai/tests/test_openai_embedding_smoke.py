import os
import unittest

from app.embedding_provider import OpenAIEmbeddingProvider


@unittest.skipUnless(
    os.getenv("RUN_OPENAI_EMBEDDING_SMOKE") == "true",
    "RUN_OPENAI_EMBEDDING_SMOKE=true is required because this test calls OpenAI",
)
class OpenAIEmbeddingSmokeTest(unittest.TestCase):
    def test_korean_embedding_matches_configured_dimension(self):
        provider = OpenAIEmbeddingProvider.from_environment()

        vectors = provider.embed(["MeetingMind 한국어 임베딩 연결 확인"])

        self.assertEqual(len(vectors), 1)
        self.assertEqual(len(vectors[0]), provider.dimension)


if __name__ == "__main__":
    unittest.main()
