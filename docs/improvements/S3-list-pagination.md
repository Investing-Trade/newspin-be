# S3. 목록 API 페이지네이션 (I-10)

- **분류**: 개선 (확장성)
- **이슈 ID**: I-10
- **브랜치**: `feat/list-pagination`
- **상태**: ✅ 구현 완료 (FE 는 이후 계약 반영)
- **기간**: 2026-09-10

---

## ① 문제

목록 API 가 전체를 무한정 반환:
- `GET /simulation/sessions/{id}/trades` — `findBySessionOrderByCreatedAtAsc` → `List` 전량.
  긴 세션이면 수백 건. 매핑 시 `trade.getStock()` 로 N+1.
- `GET /simulation/sessions` — 세션 전량.

## ② 조치

- `PageResponse<T>` record (`content, page, size, totalElements, totalPages, hasNext`) —
  Spring `PageImpl` 직렬화 경고 회피 + 안정적인 계약.
- `TradeRepository.findBySession(session, Pageable)` — `@EntityGraph("stock")` 로 N+1 방지.
- `SimulationSessionRepository.findByUser(user, Pageable)`.
- 서비스: `Page<...>` 반환. 컨트롤러: `@PageableDefault(size=20, sort="createdAt", DESC)` →
  `PageResponse.from(...)`.
- `application.yml`: `spring.data.web.pageable` — default 20, **max 100**, `one-indexed-parameters: false`.

## ③ 검증 (로컬 E2E, 거래 25건)

| 요청 | 결과 |
| --- | --- |
| `GET /trades` | page 0, size 20, totalElements 25, totalPages 2, hasNext true, content 20 |
| `GET /trades?page=1&size=10` | page 1, size 10, content 10, hasNext true |
| `GET /trades?size=500` | **size 100** (max-page-size 로 캡) |
| `GET /simulation/sessions` | page 0, content = 세션 수 |

- `TradePaginationTest` (CI): 25건 → page0 20건 hasNext, page1 5건.

## ④ FE 영향 (이후 반영)

`/trades`, `/simulation/sessions` 응답이 배열 → `{content, page, size, totalElements, totalPages, hasNext}`.
FE 는 `res.data.data.content` 로 접근하도록 수정 필요 (FE 작업 시).

## ⑤ 회고

- `PageImpl` 을 그대로 응답에 실으면 Spring 버전에 따라 JSON 구조가 바뀌어 클라이언트가 깨진다.
  얇은 `PageResponse` 로 계약을 고정.
- `Pageable` 컨트롤러 파라미터 + `spring.data.web.pageable.max-page-size` 로 "클라이언트가
  `size=100000` 보내는" 것도 서버가 막는다.
