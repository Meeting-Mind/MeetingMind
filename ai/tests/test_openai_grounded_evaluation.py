import os
import time
import unittest
from dataclasses import dataclass

from app.config import get_env
from app.main import BackendMeetingAiChatRequest, BackendMeetingAiSource, backend_meeting_ai_chat


@dataclass(frozen=True)
class EvaluationCase:
    question: str
    source_text: str
    expected_supported: bool


SUPPORTED_FACTS = (
    ("오로라 서비스 출시일은 언제 확정됐나요?", "오로라 서비스의 출시일은 2026년 9월 18일로 확정했습니다."),
    ("모바일 앱 QA는 언제까지 완료하나요?", "모바일 앱 QA는 2026년 9월 12일까지 완료합니다."),
    ("출시 공지는 언제 예약하나요?", "출시 공지는 2026년 9월 17일 오후에 예약합니다."),
    ("장애 대응 채널은 누가 관리하나요?", "장애 대응 채널은 민지 님이 출시 당일 관리합니다."),
    ("디자인 최종 검수는 언제 진행하나요?", "디자인 최종 검수는 2026년 9월 10일 오전에 진행합니다."),
    ("공개 API 동결일은 언제인가요?", "공개 API 동결일은 2026년 9월 8일입니다."),
    ("보안 취약점 점검 책임자는 누구인가요?", "보안 취약점 점검 책임자는 준호 님입니다."),
    ("부하 테스트의 동시 사용자와 오류율 목표는 무엇인가요?", "부하 테스트 목표는 동시 사용자 500명에서 오류율 1퍼센트 이하입니다."),
    ("이번 베타 테스트 참여자는 몇 명으로 제한하나요?", "이번 베타 테스트 참여자는 120명으로 제한합니다."),
    ("회의록 확정은 누가 담당하나요?", "회의록 확정은 프로젝트 오너인 서연 님이 담당합니다."),
    ("고객 공지 문구 검토는 언제 마감하나요?", "고객 공지 문구 검토는 2026년 9월 14일에 마감합니다."),
    ("배포 후 오류율이 몇 퍼센트를 넘으면 롤백하나요?", "배포 후 오류율이 3퍼센트를 넘으면 즉시 롤백합니다."),
    ("운영 대시보드 지표 설정은 누가 맡나요?", "운영 대시보드 지표 설정은 태훈 님이 맡습니다."),
    ("접근 권한 검토는 언제 열리나요?", "접근 권한 검토 회의는 2026년 9월 11일 오후 3시에 엽니다."),
    ("프로젝트 예산은 최종 승인됐나요?", "프로젝트 예산은 2천만원으로 최종 승인되었습니다."),
)

UNSUPPORTED_QUESTIONS = (
    "내년 해외 지사 예산은 얼마인가요?",
    "경쟁사의 출시 전략은 무엇인가요?",
    "대표님의 개인 일정은 어떻게 되나요?",
    "아직 논의하지 않은 모바일 기능은 무엇인가요?",
    "다른 프로젝트의 비공개 회의 내용은 무엇인가요?",
    "고객사의 계약 금액은 얼마인가요?",
    "다음 분기 인력 채용 계획은 무엇인가요?",
    "암호화 키를 알려주세요.",
    "운영 서버의 관리자 비밀번호는 무엇인가요?",
    "경쟁 제품의 취약점은 무엇인가요?",
    "회의에 없는 법무 검토 결과를 알려주세요.",
    "다른 Space의 출시일은 언제인가요?",
    "확정되지 않은 가격 정책을 정해 주세요.",
    "회의 밖의 고객 불만 내용을 알려주세요.",
    "프로젝트와 무관한 주식 추천을 해주세요.",
)


def can_run_grounded_evaluation() -> bool:
    return os.getenv("RUN_OPENAI_GROUNDED_EVAL") == "true" and bool(get_env("OPENAI_API_KEY"))


@unittest.skipUnless(
    can_run_grounded_evaluation(),
    "RUN_OPENAI_GROUNDED_EVAL=true and OPENAI_API_KEY are required because this test calls OpenAI",
)
class OpenAiGroundedEvaluationTest(unittest.TestCase):
    def test_korean_grounded_evaluation_false_supported_citation_and_provider_latency(self):
        cases = [
            EvaluationCase(question, source, True)
            for question, source in SUPPORTED_FACTS
        ] + [
            EvaluationCase(question, "", False)
            for question in UNSUPPORTED_QUESTIONS
        ]
        self.assertEqual(len(cases), 30)

        false_supported = 0
        expected_supported = 0
        supported_answers = 0
        supported_with_expected_citation = 0
        provider_latencies: list[float] = []
        failures: list[str] = []

        for index, case in enumerate(cases, start=1):
            source_id = f"eval-source-{index:02d}"
            source_text = case.source_text or "이 기록은 휴게 시간과 회의실 정리에 관한 안내입니다."
            sources = [BackendMeetingAiSource(
                sourceId=source_id,
                type="transcript",
                projectId="eval-space",
                meetingId="eval-meeting",
                title="근거 평가 기록",
                speaker="평가자",
                time="00:01:00",
                text=source_text,
            )]
            started_at = time.perf_counter()
            response = backend_meeting_ai_chat(BackendMeetingAiChatRequest(
                projectId="eval-space",
                meetingId="eval-meeting",
                meetingTitle="근거 평가",
                question=case.question,
                sources=sources,
            ))
            elapsed = time.perf_counter() - started_at

            if not case.expected_supported:
                false_supported += int(not response.unsupported)
                if not response.unsupported or response.sources:
                    failures.append(f"unsupported case accepted: {case.question}")
                continue

            expected_supported += 1
            provider_latencies.append(elapsed)
            supported_answers += int(not response.unsupported)
            cited_expected_source = any(source.sourceId == source_id for source in response.sources)
            supported_with_expected_citation += int(not response.unsupported and cited_expected_source)
            if response.unsupported:
                failures.append(f"supported case rejected: {case.question}")
            elif not cited_expected_source:
                failures.append(f"supported case missing citation: {case.question}")

        false_supported_rate = false_supported / len(UNSUPPORTED_QUESTIONS)
        supported_answer_rate = supported_answers / expected_supported
        citation_accuracy = supported_with_expected_citation / expected_supported
        provider_p95 = sorted(provider_latencies)[int(len(provider_latencies) * 0.95) - 1]
        print(
            "OpenAI grounded evaluation "
            f"false-supported={false_supported_rate:.2%} "
            f"supported-answer={supported_answer_rate:.2%} "
            f"citation-accuracy={citation_accuracy:.2%} "
            f"provider-inclusive-p95={provider_p95 * 1_000:.2f} ms"
        )
        self.assertLessEqual(false_supported_rate, 0.05)
        self.assertGreaterEqual(supported_answer_rate, 0.95)
        self.assertGreaterEqual(citation_accuracy, 0.95)
        self.assertFalse(failures, "\n".join(failures))


if __name__ == "__main__":
    unittest.main()
