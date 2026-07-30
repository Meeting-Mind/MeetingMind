# 회의록 생성 형식

AI가 만드는 회의록의 출력 형식과 markdown 조립 규칙을 고정한다.

## 배경

기존 형식은 세 가지 문제가 있었다.

1. AI가 `summary`, `decisions[]`, `actionItems[]`와 **`markdown`을 모두** 만들었다. markdown은 앞의 셋을 다시 쓴 것이라 토큰을 두 배로 쓰고, 둘이 어긋날 수 있었다. 화면은 구조화 데이터를 쓰고 내려받기는 markdown을 쓰므로 **보는 것과 받는 파일이 달라질 수 있었다**.
2. markdown에 `transcript-segment-cfee296b-9ab6-48cd-b89d-67d327f7e7c9` 같은 원시 ID가 그대로 박혔다. 사람이 읽을 수 없다. dev DB 기준 10건이 이 상태였다.
3. `summary`에 근거가 없어 요약 문장은 검증할 수 없었다.

## 원칙

- **AI는 구조화 데이터만 만든다.** markdown은 서버가 구조화 데이터로 조립한다. 그래야 둘이 어긋날 수 없다.
- **모든 문장에 근거가 붙는다.** 요약 문장도 예외가 아니다. 근거 없는 문장은 곧 지어낸 문장이다.
- **원시 ID는 사람이 보는 곳에 나오지 않는다.** 각주 번호로 바꾸고 문서 끝에 발화자와 시각으로 적는다.
- **시각은 회의 시작 기준이다**(`00:18`). 전사와 맞춰보기 쉽다.

## AI provider 출력 스키마

```json
{
  "supported": true,
  "summary": [
    { "text": "AI 검색 신규 기능의 베타 출시 범위와 일정을 확정했다.", "sourceIds": ["segment-a"] }
  ],
  "decisions": [
    { "title": "베타는 다음 달 둘째 주에 시작한다", "rationale": "로그인 사용자 중 일부 비율로 제한한다.", "sourceIds": ["segment-a"] }
  ],
  "actionItems": [
    { "title": "오답 대응 방안 마련", "assignee": null, "dueDate": null, "sourceIds": ["segment-d"], "confirmationState": "candidate" }
  ]
}
```

`markdown`은 **더 이상 AI가 만들지 않는다.**

AI service가 검증을 마친 뒤 Backend에 반환하는 내부 API 응답은 provider 출력을 그대로
노출하지 않고 다음 `schemaVersion: 2` 계약을 사용한다.

```json
{
  "schemaVersion": 2,
  "summary": [
    { "text": "AI 검색 신규 기능의 베타 출시 범위와 일정을 확정했다.", "sourceIds": ["segment-a"] }
  ],
  "decisions": [],
  "actionItems": [],
  "sources": [],
  "droppedCount": 0,
  "unsupported": false,
  "unsupportedReason": null,
  "model": "gpt-4.1-mini",
  "generationMode": "AI_DIRECT",
  "degraded": false,
  "warnings": [],
  "attemptCount": 1
}
```

- `AI_DIRECT`: 단일 context에서 생성한다.
- `AI_HIERARCHICAL`: 긴 회의를 구간별로 요약한 뒤 원본 source ID로 합성한다.
- `EXTRACTIVE_FALLBACK`: AI 생성과 구조 검증이 실패해 전사 원문을 발췌한다. 이 모드는
  `degraded=true`와 사용자 경고를 포함하며 결정·할 일을 추측하지 않는다.
- 구조 또는 citation 검증 실패의 자동 재시도는 전체 생성 요청에서 최대 1회다.

- provider 출력의 `supported`는 AI service 내부 판정값이다.
- Backend가 받는 내부 API 응답은 `unsupported`와 `unsupportedReason`을 사용한다.
- `schemaVersion: 1` 문자열 요약 응답은 rolling deployment 동안 Backend가 한시적으로 변환한다.
- `schemaVersion: 2` 전환이 확인되면 v1 호환 코드는 후속 release에서 제거한다.

### summary

- 문장 배열이다. **길이 제한을 두지 않는다** — 회의가 길면 요약도 길어져야 한다.
- 각 문장은 `sourceIds`가 비어 있으면 안 된다. 비면 그 문장을 버린다.
- 서버는 문장들을 줄바꿈으로 이어 붙여 `meeting_reports.summary` 컬럼에 저장한다. 기존 소비자(Project AI 색인)가 그대로 동작한다.

### 근거 강제

`sourceIds`가 비었거나 전달한 source 목록 밖의 ID만 있는 항목은 버린다. 버린 개수는 응답에 담아 화면이 알린다. **조용히 사라지면 안 된다** — 5건 중 3건이 버려져도 사용자가 모르는 상태가 기존 문제였다.

요약 문장이 전부 버려지면 `unsupported`로 처리한다.

## markdown 조립 규칙

서버가 조립한다. 항목은 인용 순서대로 `[1]`부터 번호를 받으며, 요약 -> 결정 -> 할 일 순서로 매긴다.

```markdown
# {회의 제목}

{날짜} {시작}–{종료} · 참석 {n}명

## 요약

{문장1}[1]
{문장2}[2]

## 결정

1. {제목} [1]
   {근거 설명}

2. {제목} [3]
   {근거 설명}

## 다음 할 일

- [ ] {제목} [4]
      담당 {이름 또는 미정} · 기한 {날짜 또는 미정}

## 근거

[1] {발화자} {회의 시작 기준 시각} — {발언}
[2] {발화자} {시각} — {발언}

---
MeetingMind가 이 회의의 검증된 근거만 사용해 생성했습니다.
```

규칙:

- 할 일은 `- [ ]` 체크박스로 쓴다. Notion과 GitHub에 붙여넣어도 체크박스로 살아난다.
- 결정이나 할 일이 없으면 해당 절을 **아예 넣지 않는다**. 빈 절을 남기면 "찾았는데 비었다"로 오해된다.
- 상태(초안/확정)는 markdown에 넣지 않는다. 화면이 알려주며, 내려받은 파일에 박히면 상태가 바뀌어도 파일은 옛 상태로 남는다.
- 제목에 "회의 보고서" 같은 접미사를 붙이지 않는다.
- 꼬리말은 항상 붙인다. 파일을 공유했을 때 출처와 범위가 문서 안에 남아야 한다.

## 실패 표현

provider가 `supported=false`를 반환하거나 검증 가능한 요약 문장이 하나도 없으면 `unsupported=true`로 반환하고 `summary`, `decisions`, `actionItems`, `sources`를 **모두 비운다**. 안내 문구를 `summary`에 넣지 않는다 — 정상 요약과 같은 자리에 들어가면 화면이 구분할 수 없다.

검증 가능한 요약 문장이 하나라도 있으면 결정이나 할 일이 비어 있어도 성공이다. 토론·브리핑 회의를 명시적 결정이나 할 일이 없다는 이유로 실패시켜서는 안 된다.

이유는 `unsupportedReason`으로 구분한다.

- `NO_EVIDENCE`: 전달할 회의 source가 없음
- `LOW_RELEVANCE`: 회의록으로 정리할 관련 근거가 부족함
- `MODEL_UNSUPPORTED`: provider가 구조화된 회의록을 만들 수 없다고 판정함
- `UNVERIFIED_OUTPUT`: 생성된 요약의 citation을 검증하지 못함

전사가 하나 이상 있으면 provider 장애나 재시도 실패 뒤에도 `EXTRACTIVE_FALLBACK` candidate를
반환한다. 전사가 전혀 없을 때만 fallback 없이 `NO_EVIDENCE`로 종료한다.

## 기존 회의록

이미 저장된 회의록은 **다시 만들지 않는다.** 확정된 것도 있어 내용이 바뀌면 이력이 어긋난다. 새로 만드는 회의록부터 이 형식을 적용한다.
