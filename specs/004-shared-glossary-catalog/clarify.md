# Clarify: Space 용어 분야 선택과 노출

## 결정사항 (2026-07-27)

1. 사용자의 “카테고리는 중복 체크 가능”은 여러 카테고리를 동시에 checkbox로 선택할 수 있다는 의미로 해석한다.
2. `기타`는 전역 `glossary_categories` 행을 만들지 않는다. 사용자 입력값은 `space_custom_glossary_categories`에 Space 범위로 저장한다.
3. 기존 클라이언트가 카테고리 필드를 보내지 않으면 이전 동작처럼 전체 분야를 구독한다. 새 UI는 선택 배열을 항상 전송한다.
4. `glossaryCategoryIds`가 명시된 경우 선택하지 않은 활성 분야는 `enabled=false`, 선택한 분야는 `enabled=true`로 저장한다.
5. 공용 용어는 읽기 전용이다. Space 등록 용어만 수정·보관할 수 있다.
