# S2-C. 시세 결측 관측화 + 프로필/보안 설정 (R-5, R-6/I-8)

- **분류**: 위험
- **이슈 ID**: R-5, R-6, I-8
- **브랜치**: `feat/stage-2-config`
- **PR**: (링크)
- **상태**: ✅ 구현 완료
- **기간**: 2026-09-09

---

## R-5. 시세 결측 조용한 0 처리

### 문제
"특정 시점 종가" 조회가 3곳에 제각각:
- `NextDayService.calculateTotalStockValue` — 결측 시 조용히 `BigDecimal.ZERO` → 보유 종목이 0원으로
  평가되어 **총자산이 실제보다 낮게** 계산되는데 로그도 메트릭도 없음.
- `PortfolioService.getCurrentPrice` — 동일하게 조용히 0.
- `TradeService.getCurrentPrice` — 직전 영업일 폴백 후, 없으면 예외.

같은 상황에 대한 처리가 서비스마다 다르고, 가장 위험한 "조용한 0"이 감지 불가.

### 조치
`StockPriceResolver` (신규, `stock` 도메인):
- `resolveCloseAsOf(stock, asOf)` → 기준일 종가 → 없으면 직전 영업일 종가 → 그것도 없으면 `Optional.empty()`.
- 매 조회마다 `newspin.stock.price.lookup{result=exact|fallback|missing}` 카운터 증가.
- `missing` 은 WARN 로그.
- `closeForValuation(...)` = 평가용, 결측이면 0 (단, 위 메트릭/로그로 이미 드러남).

3개 서비스 모두 이걸로 교체:
- NextDay/Portfolio → `closeForValuation` (0 폴백은 유지하되 이제 관측 가능)
- Trade → `resolveCloseAsOf(...).orElseThrow(INVALID_TRADE)` (거래는 시세 없으면 불가)

## R-6 / I-8. 프로필·보안 설정 분리

### 조치
- **CORS**: `SecurityConfig` 하드코딩(`localhost:5173`, `127.0.0.1:5173`) → `newspin.cors.allowed-origins`
  프로퍼티(`@Value` List). 기본값은 로컬, 운영은 `NEWSPIN_CORS_ORIGINS` 환경변수.
- **Actuator 노출 축소**: `/actuator/**` 전체 permitAll → `/actuator/health`, `/actuator/health/**`,
  `/actuator/prometheus` 만 공개. `/actuator/metrics` 등 상세는 인증 필요.
- **`application-prod.yml.example`** (신규, 커밋): `ddl-auto: validate`, `flyway.enabled: true`,
  `org.hibernate.SQL: warn`, `management.server.port: 9090`(포트 분리), `endpoint.health.show-details: never`,
  `newspin.seed.enabled: false`, 시크릿 전부 환경변수.

> `ddl-auto` 는 S0 에서 이미 `validate` 로, SQL 디버그 로그는 S0 에서 `local`/`dev` 한정으로 옮겼음.
> 여기서는 CORS·Actuator·운영 템플릿을 마무리.

## 검증

로컬:
- `newspin.cors.allowed-origins` → `List<String>` 바인딩 OK, `http://localhost:5173` preflight 200
- `/actuator/health` 200, `/actuator/prometheus` 200, `/actuator/metrics` **403** (인증 필요)
- `StockPriceResolverTest` (CI): 거래일 exact / 주말 fallback / 데이터 이전 empty·0

## ⑥ 회고

- 결측 시세를 0으로 처리하면 총자산이 조용히 틀어진다. 폴백 정책을 `StockPriceResolver` 한 곳에 모으고
  exact/fallback/missing 을 메트릭으로 노출 → 시세 데이터 구멍을 대시보드에서 확인 가능.
- CORS·관측 엔드포인트 노출·스키마 관리 정책을 프로필로 분리하고, `application-prod.yml.example` 로
  운영 설정 기준을 명문화.
