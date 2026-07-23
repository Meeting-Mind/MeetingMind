# MeetingMind Product Context

## Surface

MeetingMind의 기본 디자인 기준은 **Product surface**다. 랜딩 페이지는 제품을 소개하는 보조 Brand surface이지만, 업무 화면의 밀도와 신뢰감을 우선한다.

## Product

MeetingMind는 회의를 보고서, 결정, 태스크, 프로젝트 지식으로 연결하는 Enterprise AI Collaboration Platform이다. 회의가 끝나도 업무 맥락과 근거가 이어지는 것이 핵심 가치다.

## Users

- 프로젝트 오너와 관리자: 프로젝트, 멤버, 역할, 회의 운영을 관리한다.
- 회의 진행자와 편집자: 실시간 회의, 전사, 회의록, 태스크 후보를 검토하고 확정한다.
- 협업 팀원: 프로젝트 안에서 결정, 업무, 지식을 확인하고 다음 행동을 수행한다.
- 회의 게스트: 허용된 단일 회의 범위에서만 참여하고 결과를 확인한다.

## Jobs To Be Done

1. 회의 중 대화와 상태를 놓치지 않고 확인한다.
2. 회의 후 결정과 다음 행동을 빠르게 확정한다.
3. 과거 회의의 근거를 권한 범위 안에서 다시 찾는다.
4. 프로젝트의 현재 업무와 지식을 한 맥락에서 관리한다.

## Design Voice

차분함, 정확성, 신뢰.

- 정보를 먼저 보여주고 장식은 최소화한다.
- AI 결과는 범위와 출처를 항상 함께 보여준다.
- 권한 부족은 숨기기보다 가능한 행동과 이유를 설명한다.
- 마케팅 과장, 가짜 성공 상태, 의미 없는 수치 표현은 사용하지 않는다.

## References

- 참고: Linear, Notion, Stripe Dashboard, Vercel, OpenAI, Saltlux
- 지향: 기업용 정보 밀도, 명확한 계층, 절제된 블루 포인트, 안정적인 업무 흐름
- 제외: 과도한 카드 중첩, 보라색 AI 그라데이션, 장식성 3D, 과도한 모션, 소비자 앱 같은 과장된 CTA

## Non-Negotiables

- 사용자 흐름, 권한, AI 검색 범위는 `specs/001-meetingmind-core/frontend-refactor-plan.md`를 최우선 기준으로 한다.
- Meeting AI는 현재 회의만, Project AI는 접근 가능한 회의와 공식 Project Knowledge만 사용한다.
- API, 인증, 권한, 라우트, 데이터 모델, 상태 흐름은 디자인 작업에서 변경하지 않는다.
