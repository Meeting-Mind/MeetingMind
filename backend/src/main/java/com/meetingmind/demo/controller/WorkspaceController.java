package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.WorkspaceResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    @GetMapping
    public WorkspaceResponse getWorkspace() {
        var liveOverview = new WorkspaceResponse.Overview(
                "3회차 API 설계 회의",
                "Space: FinPilot Renewal · 2026.06.21 · 14:00 시작 예정 · 초대된 참여자 8명",
                List.of("Ready", "입장 프리뷰", "권한 보호")
        );

        var workspaceOverview = new WorkspaceResponse.Overview(
                "내 워크스페이스",
                "참여 중인 프로젝트를 확인하고, 이어서 봐야 할 회의 흐름으로 바로 이동합니다.",
                List.of("2개 프로젝트", "최근 업데이트 2건")
        );

        var meetingOverview = new WorkspaceResponse.Overview(
                "3회차 전용 Meeting AI",
                "현재 회의의 STT 원문, AI 보고서, 결정사항, Action Item만 검색",
                List.of("Scoped Search", "Project 전체 제외")
        );

        var projectOverview = new WorkspaceResponse.Overview(
                "FinPilot Renewal - 프로젝트 개요",
                "Space 멤버 8명 · 진행 회의 3건 · 최신 보고서 1건",
                List.of("Project AI", "권한 기반 검색")
        );

        var reportOverview = new WorkspaceResponse.Overview(
                "3회차 AI 보고서 편집",
                "자동 생성된 회의 보고서를 대화형 Agent로 후처리하는 화면",
                List.of("Report Draft", "Project 문서 저장 가능")
        );

        return new WorkspaceResponse(
                new WorkspaceResponse.WorkspaceHome(
                        workspaceOverview,
                        new WorkspaceResponse.WorkspaceTodayMeeting(
                                "3회차 API 구조 논의",
                                "FinPilot Renewal",
                                "오늘 오후 2:00",
                                "참여 예정 8명",
                                "권한 모델과 보고서 저장 흐름을 정리할 예정입니다.",
                                "/live-meeting"
                        ),
                        List.of(
                                new WorkspaceResponse.WorkspaceSpace(
                                        "FinPilot Renewal",
                                        "멤버 8명",
                                        "진행 회의 3건",
                                        "어제 21:08 업데이트",
                                        "리뉴얼 일정과 사용자 흐름 개선안을 논의하는 협업 공간입니다.",
                                        "/project-overview"
                                ),
                                new WorkspaceResponse.WorkspaceSpace(
                                        "Campus Admin Assistant",
                                        "멤버 5명",
                                        "진행 회의 2건",
                                        "06.19 업데이트",
                                        "운영 자동화 기능과 관리자 권한 구조를 검토하는 프로젝트입니다.",
                                        "/project-overview"
                                )
                        ),
                        List.of(
                                new WorkspaceResponse.LabeledItem("FinPilot Renewal · 액션 아이템 2건 갱신", "오늘 11:10"),
                                new WorkspaceResponse.LabeledItem("Campus Admin Assistant · 킥오프 회의록", "06.19")
                        )
                ),
                new WorkspaceResponse.LiveMeeting(
                        liveOverview,
                        "MM-03A",
                        "14:00",
                        List.of(
                                "김진수 · 접속 완료",
                                "박서윤 · 오디오 체크 중",
                                "최민호 · 접속 완료",
                                "서다은 · 곧 입장"
                        ),
                        List.of(
                                new WorkspaceResponse.LabeledItem("카메라 권한 허용", "브라우저에서 정상 인식됨"),
                                new WorkspaceResponse.LabeledItem("마이크 입력 확인", "입력 레벨 정상"),
                                new WorkspaceResponse.LabeledItem("도메인 용어사전 연결", "프로젝트 용어 42개 로드"),
                                new WorkspaceResponse.LabeledItem("화면 공유 준비", "발표자가 시작 후 선택 가능")
                        ),
                        List.of(
                                new WorkspaceResponse.MeetingAccessMember("이미주", "Host · Product Manager", "편집 가능", "회의 시작, 참여자 권한 변경"),
                                new WorkspaceResponse.MeetingAccessMember("김진수", "Backend Lead", "발표 가능", "화면 공유, 문서 업로드"),
                                new WorkspaceResponse.MeetingAccessMember("박서윤", "Product Designer", "참여 가능", "음성 참여, 채팅, 자막 확인"),
                                new WorkspaceResponse.MeetingAccessMember("최민호", "Data Engineer", "참여 대기", "초대됨, 시작 후 입장 승인")
                        )
                ),
                new WorkspaceResponse.MeetingAi(
                        meetingOverview,
                        List.of(
                                new WorkspaceResponse.TranscriptRow("06:10:03", "김진수", "ERD 구조를 수정해야 합니다..."),
                                new WorkspaceResponse.TranscriptRow("06:12:21", "이미주", "권한 관리는 회의 단위로 분리하는 것이..."),
                                new WorkspaceResponse.TranscriptRow("06:14:08", "박서윤", "API 명세는 오늘 확정하죠...")
                        ),
                        List.of(
                                new WorkspaceResponse.LabeledItem("회의/프로젝트 권한 분리", "06:12:21"),
                                new WorkspaceResponse.LabeledItem("RAG 범위 사용자 권한 기준 적용", "06:13:02")
                        ),
                        List.of(
                                new WorkspaceResponse.LabeledItem("박서윤 · 접근 제어 UI 설계", "Owner"),
                                new WorkspaceResponse.LabeledItem("김진수 · ERD 수정안 문서화", "Owner")
                        ),
                        List.of(
                                new WorkspaceResponse.ChatMessage("user", "김진수가 API 구조에 대해 정확히 어떤 의견을 냈어?"),
                                new WorkspaceResponse.ChatMessage("ai", "김진수는 ERD의 외래키 제약 누락을 지적하며 구조 수정을 제안했습니다. 출처: 06:10:03 STT 원문"),
                                new WorkspaceResponse.ChatMessage("user", "10분 이후에 논의된 내용을 요약해줘."),
                                new WorkspaceResponse.ChatMessage("ai", "10분 이후엔 권한 분리 구조와 RAG 검색 범위 연동이 논의됐습니다. 출처: 06:12:21-06:12:55")
                        ),
                        List.of(
                                new WorkspaceResponse.LinkItem("최종 합의된 내용만 보여줘", "#"),
                                new WorkspaceResponse.LinkItem("김진수 발언만 모아줘", "#"),
                                new WorkspaceResponse.LinkItem("회의 후반부 요약해줘", "#")
                        )
                ),
                new WorkspaceResponse.ProjectOverview(
                        projectOverview,
                        List.of(
                                new WorkspaceResponse.Metric("진행 회의", "3", "이번 주 1건 예정"),
                                new WorkspaceResponse.Metric("최신 보고서", "1", "3회차 생성 완료"),
                                new WorkspaceResponse.Metric("열린 Action Item", "5", "내 담당 2건"),
                                new WorkspaceResponse.Metric("최근 결정사항", "2", "오늘 1건 갱신")
                        ),
                        "React, Spring Boot, FastAPI, PostgreSQL, pgvector",
                        List.of(
                                new WorkspaceResponse.MeetingRow("#01", "킥오프 - 프로젝트 범위 정의", "06.02", "완료"),
                                new WorkspaceResponse.MeetingRow("#02", "ERD 설계 리뷰", "06.09", "완료"),
                                new WorkspaceResponse.MeetingRow("#03", "API 구조 논의", "06.16", "보고서 생성됨"),
                                new WorkspaceResponse.MeetingRow("#04", "보안 점검 (비공개)", "06.23", "예정")
                        ),
                        List.of(
                                new WorkspaceResponse.LabeledItem("3회차 AI 보고서", "오늘 15:24"),
                                new WorkspaceResponse.LabeledItem("권한 모델 정리본", "어제 21:08"),
                                new WorkspaceResponse.LabeledItem("ERD v2", "06.19")
                        ),
                        List.of(
                                new WorkspaceResponse.LinkItem("왜 PostgreSQL을 선택했어?", "/meeting-ai"),
                                new WorkspaceResponse.LinkItem("내가 맡기로 한 업무가 뭐야?", "/meeting-ai"),
                                new WorkspaceResponse.LinkItem("지금까지 API 설계 논의 흐름 정리", "/meeting-ai")
                        )
                ),
                new WorkspaceResponse.ReportAgent(
                        reportOverview,
                        "3회차 회의 보고서 - API 구조 논의",
                        "2026.06.16 · 참여자 4명 · AI 자동 생성",
                        List.of(
                                new WorkspaceResponse.ReportDecision("ERD 수정", "외래키 제약 추가, 3정규형 재검토", "박서연 검토"),
                                new WorkspaceResponse.ReportDecision("권한 구조", "회의 단위 권한 분리 적용", "RAG 범위 연동")
                        ),
                        List.of(
                                new WorkspaceResponse.LabeledItem("report.decisions", "표 형식 반영"),
                                new WorkspaceResponse.LabeledItem("report.summary", "문체 조정"),
                                new WorkspaceResponse.LabeledItem("report.actions", "담당자 라벨 유지")
                        ),
                        List.of(
                                new WorkspaceResponse.ChatMessage("user", "결정사항을 표 형태로 정리해줘."),
                                new WorkspaceResponse.ChatMessage("ai", "결정사항 섹션을 표로 변환했습니다. 우측 미리보기에 반영됐어요."),
                                new WorkspaceResponse.ChatMessage("user", "교수님 제출용 문체로 변경해줘."),
                                new WorkspaceResponse.ChatMessage("ai", "전체 문체를 격식체로 다시 작성 중입니다...")
                        )
                )
        );
    }
}
