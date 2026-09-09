# S3. StockPrice 반복 조회 배치화 (I-2)

- **분류**: 개선 (성능)
- **이슈 ID**: I-2
- **브랜치**: `perf/stock-price-batch`
- **상태**: ✅ 구현 완료
- **기간**: 2026-09-09

---

## ① 문제

포트폴리오 평가가 보유 종목 수 N 만큼 시세 쿼리를 반복:

- `PortfolioService.getPortfolioOverview` — 보유 종목 루프 안에서 `stockPriceResolver.closeForValuation(stock, date)`
- `NextDayService.calculateTotalStockValue` — 동일

`StockPriceResolver` 는 종목별로 exact 1회(+결측 시 fallback 1회) 조회 → N종목이면 N~2N 쿼리.

## ② 계측 (Before, 6종목 보유)

| 엔드포인트 | 전체 select | 그중 `stock_price` |
| --- | --- | --- |
| `GET /portfolio` | 9 | **6** (= N) |
| `POST /next-day` | 11 | **6** (= N) |

## ③ 조치

- `StockPriceResolver.closesForValuation(Collection<Stock>, LocalDate)` — 종목 목록을 받아
  `Map<stockId, close>` 반환. 쿼리는 종목 수와 무관하게 1~2회:
  - `findByStockIdsAndPriceDate` — `stock_id IN (...) AND price_date = :date` (1회)
  - 결측분만 `findLatestBeforeByStockIds` — 상관 서브쿼리로 종목별 직전 종가 (1회)
  - 그래도 없으면 0 + `missing` 메트릭
- 두 서비스의 종목 루프를 "배치 조회 → 맵 lookup" 으로 교체.
- 사용처 사라진 `PortfolioService.getCurrentPrice` private 메서드 제거.

## ④ 검증 (After, 6종목 보유)

| 엔드포인트 | 전체 select | `stock_price` | 변화 |
| --- | --- | --- | --- |
| `GET /portfolio` | 4 | **1** | 6 → 1 |
| `POST /next-day` | 6 | **1** | 6 → 1 |

- 스케일: Before O(N), After O(1). 20종목이면 20 → 1.
- 평가액 계산 결과 동일 확인 (`totalStockValue`, `items` 정상).
- `StockPriceBatchTest` (CI): 6종목 보유 시 `prepareStatementCount <= 5`.

## ⑤ 회고

- 결측 폴백을 위한 "종목별 직전 종가"는 JPQL 상관 서브쿼리로 1쿼리 처리 가능
  (`price_date = (SELECT MAX ... WHERE ... < :date)`).
- 시드 데이터는 모든 거래일 시세가 있어 fallback 쿼리가 실행되지 않지만, 결측 대비 경로는 유지.
- 단일 조회 API(`TradeService.getCurrentPrice`)는 종목 1개라 배치 대상 아님 — `resolveCloseAsOf` 유지.
