# S1. 심각 결함 수정 (스테이지 1)

- **분류**: 심각
- **이슈 ID**: C-1, C-5, C-3, C-4 (+ S-1)
- **브랜치**: `feat/stage-1-critical`
- **PR**: (링크)
- **상태**: 🚧 진행 중
- **기간**: 2026-09-09 ~

신뢰도를 훼손하는 결함 4건. 각 항목 착수 전 특성화 테스트를 깔고 시작한다.

---

## 1. AIService 하드코딩 테스트 호출 제거 (C-1)

### 문제
`AIService.analyzeUserJudgment` 진입 직후, 실제 로직과 무관한 더미 JSON을
`newspin-ai` 로 POST 하고 상태코드/본문을 로그만 남기는 블록이 있었다.
매 요청마다 불필요한 외부 HTTP 호출 1회가 프로덕션 경로 앞에 실행됨.

### 조치
블록 전체 삭제. AI 서버 상태 확인이 필요하면 별도 헬스체크로 분리 (이 스테이지 범위 밖).
`java.net.http.*` import 는 같은 메서드 뒤쪽 실제 호출에서 계속 사용하므로 유지.

### 검증
컴파일 + 기존 흐름 유지. 요청당 외부 호출 2회 → 1회.

---

## 2. 미래 시세 유출 차단 (C-5, S-1)

### 문제
`StockService.getStockPriceHistoryAroundDate` 가 기준일(`date`) 기준 **이전 5영업일 + 당일 + 이후 5영업일**을
반환. 유저가 오늘 뉴스를 보고 매수를 고민하는 시점에 `/stocks/price-range?date=<오늘>` 을 부르면
"이 종목이 며칠 뒤 오르는지/내리는지"가 응답에 이미 들어있음 → 학습 서비스의 핵심 전제 붕괴.
FE `develop` 브랜치의 차트(`fetchPriceHistory`)가 실제로 이 미래 구간까지 그림.

### 원인
- 노출 가능 데이터의 상한(= 판단 시점)을 강제하는 규칙이 없음 (S-1).
- `findTop5...GreaterThan...` 로 기준일 이후 구간을 명시적으로 조회.

### 조치
- `getStockPriceHistoryUpTo(stockCode, asOfDate)` — `findTop11ByStockAndPriceDateLessThanEqualOrderByPriceDateDesc`
  로 **기준일 이하만** 조회. 이후 구간 조회 로직 제거.
- 차트 포인트 수 유지를 위해 이전 10거래일 + 기준일 = 11포인트.
- 종목당 쿼리 3~4회 → 1~2회 (부수 효과, I-2 일부).
- FE 무영향: FE 는 항상 서버 권위 값 `dayData.simulationDate` 를 `date` 로 전달.

### 검증
- 회귀 테스트 `StockServiceLookaheadTest` — "기준일 이후 시세는 반환되지 않는다" / 전체 종목 조회도 동일.
- 로컬 E2E: `?date=2020-02-03` → `2020-01-16 ~ 2020-02-03` (미래 없음),
  `005930?date=2020-03-13` → `2020-02-28 ~ 2020-03-13`, `isEventDate` = `2020-03-13`.

### 남은 것 (이후 스테이지)
- `/stocks/**` 는 세션에 묶여있지 않아, 악의적 클라이언트가 임의 미래 `date` 를 직접 넘기는 것까지는 못 막음.
  세션 종속 엔드포인트(`currentSimulationDate` 를 하드 상한으로) 로 대체하는 것을 하드닝 항목으로 남김.

## 3. 모의투자 가격 검증 재설계 (C-3)

(작성 예정)

## 4. GeminiService 응답 파싱 방어 + 재시도 (C-4)

(작성 예정)

---

## ⑥ 회고 / 자소서·면접 문장 초안

(스테이지 종료 시 취합)
