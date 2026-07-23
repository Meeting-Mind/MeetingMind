import unittest

from app.provider_url import local_provider_base_url_error, local_provider_base_url_is_compatible


class ProviderUrlTest(unittest.TestCase):
    def test_accepts_absolute_non_openai_http_urls(self):
        self.assertIsNone(
            local_provider_base_url_error("http://llm.internal:8000/v1", label="AI_TEXT_BASE_URL")
        )
        self.assertTrue(local_provider_base_url_is_compatible("https://models.onprem.example/v1"))

    def test_rejects_relative_or_public_openai_urls_without_exposing_full_url(self):
        relative_error = local_provider_base_url_error("/v1", label="AI_TEXT_BASE_URL")
        openai_error = local_provider_base_url_error(
            "https://api.openai.com/v1/organizations/secret-path",
            label="AI_EMBEDDING_BASE_URL",
        )

        self.assertEqual(relative_error, "AI_TEXT_BASE_URL must be an absolute http(s) URL")
        self.assertEqual(openai_error, "AI_EMBEDDING_BASE_URL must not point to api.openai.com")
        self.assertNotIn("secret-path", openai_error)
        self.assertFalse(local_provider_base_url_is_compatible("https://api.openai.com/v1"))

    def test_rejects_credentials_query_or_fragment_without_exposing_values(self):
        userinfo_error = local_provider_base_url_error(
            "https://token:secret@llm.internal:8000/v1",
            label="AI_TEXT_BASE_URL",
        )
        query_error = local_provider_base_url_error(
            "https://llm.internal:8000/v1?api_key=secret",
            label="AI_TEXT_BASE_URL",
        )
        fragment_error = local_provider_base_url_error(
            "https://llm.internal:8000/v1#secret",
            label="AI_EMBEDDING_BASE_URL",
        )

        self.assertEqual(userinfo_error, "AI_TEXT_BASE_URL must not include userinfo credentials")
        self.assertEqual(query_error, "AI_TEXT_BASE_URL must not include query or fragment")
        self.assertEqual(fragment_error, "AI_EMBEDDING_BASE_URL must not include query or fragment")
        self.assertNotIn("secret", userinfo_error)
        self.assertNotIn("secret", query_error)
        self.assertNotIn("secret", fragment_error)
        self.assertFalse(local_provider_base_url_is_compatible("https://token:secret@llm.internal:8000/v1"))


if __name__ == "__main__":
    unittest.main()
