# S2-B. 동시성 — 세션 비관적 락 (R-3, R-4)

- **분류**: 위험
- **이슈 ID**: R-3, R-4
- **브랜치**: `feat/stage-2-concurrency`
- **PR**: (링크)
- **상태**: ✅ 구현 완료
- **기간**: 2026-09-09

---

## ① 문제

같은 세션에 대한 동시 요청(더블클릭, 빠른 연타, 재시도)에서:

- **R-4 잔고 lost update**: `TradeService.executeBuy` 가 `session.getCurrentCapital()` 을 읽어
  잔고 확인 → `session.decreaseCapital()` 로 차감. 두 거래가 같은 잔고를 읽으면 한 쪽 차감이
  덮여, 실제보다 잔고가 많이 남고 두 거래 모두 체결됨.
- **R-3 AssetHistory 유니크 위반**: `NextDayService.proceedToNextDay` 가 `(session_id, record_date)`
  유니크 제약이 있는 `asset_history` 에 저장. 동시 호출 시 두 번째가 `DataIntegrityViolationException`
  → 500.

## ② 원인

세션 잔고·진행일을 바꾸는 두 경로(`executeTrade`, `proceedToNextDay`)가 세션을 그냥 `findById` 로
읽어 아무 락 없이 수정. 한 세션의 쓰기가 직렬화되지 않음.

## ③ 해결

| 옵션 | 채택 | 근거 |
| --- | --- | --- |
| `@Version` 낙관적 락 + 재시도 | ❌ | 재시도 경계가 `@Transactional` 메서드 밖이어야 해 별도 파사드/spring-retry 필요. 마이그레이션도 추가 |
| **비관적 쓰기 락** (`SELECT ... FOR UPDATE`) | ✅ | 한 세션의 거래·진행은 본래 직렬. 트랜잭션이 짧고(외부 호출 없음) 락 경합은 세션 단위라 무시 가능 |

- `SimulationSessionRepository.findByIdForUpdate` — `@Lock(PESSIMISTIC_WRITE)`.
- `TradeService.executeTrade`, `NextDayService.proceedToNextDay` 만 이걸로 세션 로딩
  (조회 경로 `getTradeHistory`, `getCurrentDayData` 는 그대로 `findById`).
- `GlobalExceptionHandler`: `DataIntegrityViolationException` → 409 `C409` (기존엔 500).

## ④ 구현

- `findByIdForUpdate(@Lock PESSIMISTIC_WRITE)` 추가.
- `TradeService`: `getSession`(읽기) / `getSessionForUpdate`(쓰기) 로 분리, 검증 로직은 `validate` 공유.
- `NextDayService`: 동일하게 `getSession` / `getSessionForUpdate` 분리.
- `GlobalExceptionHandler` + `DataIntegrityViolationException` 핸들러.
- (chore) CI `actions/checkout@v5`, `setup-java@v5`, `upload-artifact@v5`.

## ⑤ 검증

로컬 E2E (compose):

| 시나리오 | 결과 |
| --- | --- |
| 잔고 100만, 10개 동시 매수 @57,200 | 10건 전부 체결, `currentCapital = 428,000` (정확히 100만 − 57.2만) |
| 잔고 부족분까지 동시 매수 (통합 테스트) | 성공 3건 + `INSUFFICIENT_CAPITAL` 7건, 잔고 ≥ 0 |
| 5개 동시 다음날 진행 | 5건 전부 200, `asset_history` 5개 서로 다른 날짜, 500 없음 |

- 통합 테스트 `SessionConcurrencyTest` (CI): 위 3가지.

## ⑥ 회고 / 자소서·면접 문장 초안

- **동시성은 사전에 인지하고 막는다**: 유니크 제약이 있는데 동시성 제어가 없으면, 정상 흐름에선 안 보이다가
  더블클릭 한 번에 500 이 난다. 잔고 lost update 는 더 조용해서 — 로그도 안 남고 데이터만 틀어진다.
- **낙관 vs 비관 선택**: "한 사용자가 자기 세션에 동시 요청을 얼마나 보내겠나"를 따져 경합이 세션 단위로
  국소적이고 트랜잭션이 짧다는 점에서 비관적 락을 골랐다. 낙관적 락 + 재시도는 재시도 경계를 트랜잭션
  밖으로 빼야 해서 구조가 더 복잡해진다.
- CRM 프로젝트의 DB 커넥션/자원 경합 경험과 같은 결.
