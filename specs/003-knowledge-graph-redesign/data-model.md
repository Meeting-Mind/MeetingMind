# Data Model: Obsidian형 Note/Link (2단계 백엔드 제안)

## 현재 상태 (1단계가 소화하는 구조)

- `ProjectKnowledgeItem`: type(report|decision|manual|external), contentPreview, embeddingStatus.
- `KnowledgeGraphResponse`: clusters + edges(from/to/similarity). **엣지는 전부 임베딩 유사도 파생** — 명시적 링크 개념이 없음.
- 프론트 어댑터 `buildGraphView`가 이를 화면 모델(GraphNodeVM/GraphLinkVM)로 정규화. `edgeType`이 생기면 explicit 링크로 자동 분류되도록 이미 대응해둠.

## 목표 모델 (옵시디언 구조)

### Note — 지식의 단일 1급 엔티티
회의록·결정·용어·액션·수동 지식을 모두 Note로 수렴한다.

```
Note {
  id: UUID
  spaceId: UUID
  noteType: REPORT | DECISION | MANUAL | EXTERNAL | GLOSSARY | ACTION | TRANSCRIPT | SUMMARY
  title: string            # space 내 유니크 (위키링크 해석 키)
  contentMd: text          # 마크다운 본문, [[제목]] 위키링크 허용
  sourceMeetingId: UUID?   # 회의 파생 노트의 출처
  embeddingStatus: PENDING | PROCESSING | COMPLETED | FAILED
  createdAt / updatedAt
}
```

### Link — 명시적 연결의 1급 엔티티

```
Link {
  id: UUID
  spaceId: UUID
  sourceNoteId: UUID
  targetNoteId: UUID?      # null이면 미생성 노트 (targetTitle로 대기)
  targetTitle: string      # 위키링크 원문 제목
  kind: EXPLICIT | SIMILARITY | DERIVED
  anchor: { start: int, end: int }?   # 본문 내 위치 (백링크 미리보기용)
  similarity: float?       # kind=SIMILARITY일 때
}
```

- 저장 시 `contentMd`에서 `[[...]]`를 파싱해 EXPLICIT Link를 upsert.
- 임베딩 유사도 엣지는 삭제하지 않고 `kind=SIMILARITY`로 공존 (화면에서 실선/점선 구분).
- 백링크 = `Link where targetNoteId = :id`, 언링크드 멘션 = 제목 전문검색 − 기존 Link.

## API 계약 (2단계)

```
GET  /api/v1/spaces/{spaceId}/graph?types=&tags=&minSimilarity=&cursor=
     → { nodes[], links[], partial }          # 전역 그래프, 페이지네이션
GET  /api/v1/spaces/{spaceId}/notes/{noteId}/graph?depth=2
     → { nodes[], links[] }                   # 로컬 그래프
GET  /api/v1/spaces/{spaceId}/notes/{noteId}/backlinks
     → { backlinks[], unlinkedMentions[] }
```

- 권한: 모든 쿼리는 RAG와 동일한 권한 선필터를 통과한 노트만 반환 (constitution 5).
- 기존 `/knowledge/*` 엔드포인트는 Note API로 이행 완료 시까지 유지.

## 프론트 이행 비용
- `buildGraphView` 어댑터만 교체 지점. GraphNodeVM/GraphLinkVM 화면 모델은 그대로 유지된다.
