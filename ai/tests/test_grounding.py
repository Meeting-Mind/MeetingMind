import unittest

from app.grounding import (
    GROUNDED_ANSWER_SCHEMA,
    MalformedGroundedOutput,
    REPORT_SCHEMA,
    TASK_CANDIDATES_SCHEMA,
    evaluate_evidence,
    parse_grounded_answer,
    strict_json_schema_format,
)


class EvidenceGateTest(unittest.TestCase):
    def test_distinguishes_no_evidence_and_low_relevance(self):
        no_evidence = evaluate_evidence([])
        low_relevance = evaluate_evidence([0.12, 0.29])

        self.assertFalse(no_evidence.supported)
        self.assertEqual(no_evidence.reason, "NO_EVIDENCE")
        self.assertFalse(low_relevance.supported)
        self.assertEqual(low_relevance.reason, "LOW_RELEVANCE")

    def test_accepts_unmeasured_prefiltered_context(self):
        decision = evaluate_evidence([None, 0.1])

        self.assertTrue(decision.supported)
        self.assertIsNone(decision.reason)


class GroundedAnswerTest(unittest.TestCase):
    def test_accepts_only_citations_from_the_allowlist(self):
        result = parse_grounded_answer(
            '{"supported":true,"answer":"검증된 답변","sourceIds":["source-1"]}',
            ["source-1", "source-2"],
        )

        self.assertTrue(result.supported)
        self.assertEqual(result.answer, "검증된 답변")
        self.assertEqual(result.source_ids, ("source-1",))

    def test_rejects_missing_or_forged_citations(self):
        missing = parse_grounded_answer(
            '{"supported":true,"answer":"근거 없는 답변","sourceIds":[]}',
            ["source-1"],
        )
        forged = parse_grounded_answer(
            '{"supported":true,"answer":"위조 근거 답변","sourceIds":["source-999"]}',
            ["source-1"],
        )

        self.assertFalse(missing.supported)
        self.assertEqual(missing.reason, "UNVERIFIED_OUTPUT")
        self.assertFalse(forged.supported)
        self.assertEqual(forged.reason, "UNVERIFIED_OUTPUT")

    def test_maps_provider_unsupported_and_rejects_malformed_output(self):
        unsupported = parse_grounded_answer(
            '{"supported":false,"answer":"","sourceIds":[]}',
            ["source-1"],
        )

        self.assertFalse(unsupported.supported)
        self.assertEqual(unsupported.reason, "MODEL_UNSUPPORTED")
        with self.assertRaises(MalformedGroundedOutput):
            parse_grounded_answer("plain text", ["source-1"])


class StructuredOutputSchemaTest(unittest.TestCase):
    def test_all_strict_schemas_are_closed_and_require_every_property(self):
        for schema in (GROUNDED_ANSWER_SCHEMA, REPORT_SCHEMA, TASK_CANDIDATES_SCHEMA):
            with self.subTest(schema=schema):
                self.assert_closed_object_schema(schema)

    def test_response_format_enables_strict_json_schema(self):
        response_format = strict_json_schema_format(
            "meetingmind_test",
            GROUNDED_ANSWER_SCHEMA,
        )

        self.assertEqual(response_format["type"], "json_schema")
        self.assertEqual(response_format["name"], "meetingmind_test")
        self.assertTrue(response_format["strict"])
        self.assertIs(response_format["schema"], GROUNDED_ANSWER_SCHEMA)

    def assert_closed_object_schema(self, schema):
        if schema.get("type") == "object":
            self.assertFalse(schema.get("additionalProperties", True))
            self.assertEqual(set(schema.get("required", [])), set(schema.get("properties", {})))
            for child in schema.get("properties", {}).values():
                self.assert_closed_object_schema(child)
        elif schema.get("type") == "array":
            self.assert_closed_object_schema(schema["items"])
