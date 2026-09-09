# S3. Redis 캐시 계층 도입 (I-3)

- **분류**: 개선 (성능)
- **이슈 ID**: I-3
- **브랜치**: `perf/redis-cache`
- **상태**: ✅ 구현 완료
- **기간**: 2026-09-09

---

## ① 문제

기획 문서상 "Redis 캐싱(시나리오/시계열) 사용 중" 이지만 실제 Redis 는 JWT refresh token·이메일
인증코드 저장에만 쓰였고, 읽기 캐시 계층이 없었다.

가장 캐시 효과가 큰 대상: **주가 히스토리 조회**(`GET /stocks/{code}/price-range`, `/stocks/price-range`).
과거 확정 데이터의 순수 함수라 (종목, 기준일)이 같으면 결과가 항상 같다. 차트가 열려 있는 동안
같은 (종목, 날짜)로 반복 호출된다.

## ② 조치

- `spring-boot-starter-cache` + `@EnableCaching`
- `CacheConfig`:
  - `priceHistory`, `priceHistoryAll` 캐시, TTL 6h (불변 데이터라 무효화 불필요, TTL 은 안전망)
  - `CacheErrorHandler = LoggingCacheErrorHandler` → **Redis 장애 시 예외 전파 없이 원본 조회로 진행**
  - 값 직렬화는 기본 JDK — 대상 DTO(`StockPriceHistoryResponse`, `StockPriceHistoryItem`)에
    `Serializable` 추가
- `StockService`:
  - `getStockPriceHistoryUpTo` → `@Cacheable("priceHistory", key = "#stockCode + ':' + #asOfDate")`
  - `getAllStocksPriceHistoryUpTo` → `@Cacheable("priceHistoryAll", key = "#asOfDate.toString()")`
    (LocalDate 를 그대로 키로 쓰면 로케일 의존 포맷(`20. 3. 5.`)이 되어 `.toString()` 강제)
- 테스트 프로필은 `spring.cache.type: simple` (인메모리) — Redis 컨테이너 없이 캐싱 동작만 검증.

## ③ 검증 (로컬, dev, Redis 6380)

| | Before (캐시 없음) | After |
| --- | --- | --- |
| `/stocks/005930/price-range` 2번째 호출 시세 쿼리 | 2 | **0** |
| 단일 종목 조회 지연 | miss 233 ms | hit p50 **114 ms** |
| `/stocks/price-range`(전체) 2번째 호출 시세 쿼리 | 20+ | **0** |
| Redis 키 | - | `priceHistory::005930:2020-02-10`, `priceHistoryAll::2020-03-05` |

- `StockPriceHistoryCacheTest` (CI): 2번째 조회 `prepareStatementCount == 0`.

## ④ 회고

- 캐시 1순위는 "순수 함수 + 불변 입력". 시세 히스토리는 (종목, 과거 날짜) → 확정 결과라 무효화 로직이
  아예 없어도 된다.
- `@Cacheable` key SpEL 에서 `LocalDate` 를 문자열 연결 없이 쓰면 `RedisCache` 가 로케일 포맷으로
  직렬화 → 서버 로케일에 따라 키가 갈린다. 명시적 `.toString()` 필요.
- Redis 가 캐시 용도로 죽어도 서비스는 계속돼야 한다 → `CacheErrorHandler` 로 degrade.
- 남은 후보: 종목 메타(`Stock`)·이벤트 정답지. 엔티티 캐싱은 detached/lazy 이슈가 있어 DTO 단위로만.
