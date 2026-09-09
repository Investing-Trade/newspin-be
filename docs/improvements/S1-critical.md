# S1. 심각 결함 수정 (스테이지 1)

- **분류**: 심각
- **이슈 ID**: C-1, C-5, C-3, C-4 (+ S-1)
- **브랜치**: `feat/stage-1-critical`
- **PR**: (링크)
- **상태**: ✅ 구현 완료 (PR 리뷰 대기)
- **기간**: 2026-09-09

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

### 문제
`TradeService.executeTrade` 에서 `validatePrice(request.getPrice(), currentPrice)` 호출부가
주석 처리됨 ("모의투자이므로 가격 검증 제거"). 클라이언트가 어떤 가격을 보내도 그대로 통과.
체결은 서버 시세로 하지만, 클라이언트가 본 가격과 서버 시세가 어긋나도 감지·거절하지 못함.

### 원인
원래 `validatePrice` 가 `requestPrice.compareTo(currentPrice) != 0` (완전 일치) 라 타이밍·날짜
차이로 항상 실패 → 팀이 검증 자체를 껐음.

### 조치
- `validatePrice(clientPrice, serverPrice)`: 서버 시세 대비 **±1% 허용 오차** 검증.
  안이면 통과, 벗어나면 `PRICE_MISMATCH`(S008, 409) 로 거절.
- 체결가는 **항상 서버 시세** (클라이언트 값 미신뢰).
- 정상 흐름에서 FE 는 서버와 같은 일봉 종가를 `price` 로 보내므로 오탐 없음.
  스테일/조작된 값(하드코딩 fallback 등)은 걸러짐.

### 검증
- `TradeServicePriceValidationTest`: 일치 / +0.5%(허용) / +10%(거절) 3케이스.
- 로컬 E2E: 정가 57,200 → 체결, +10% → `S008` 거절.

## 4. GeminiService 응답 파싱 방어 + 재시도 (C-4)

### 문제
- 응답을 `(Map)(List)...(String)` 캐스팅 체인으로 파싱. Gemini 가 안전 필터로 콘텐츠를 막으면
  `content`/`parts` 가 없어 NPE (broad `catch(Exception)` 가 삼켜 `"...오류: null"` 반환).
- 재시도·타임아웃 없음. `geminiRestClient` 에 타임아웃 미설정(무한 대기 가능).
- 429/5xx 일시 오류도 그냥 실패.

### 조치
- `geminiRestClient`: connect 5s / read 20s 타임아웃.
- `parseText` — null 안전 네비게이션 + `finishReason`/`promptFeedback.blockReason` 검사.
  - 차단(SAFETY/RECITATION/…) → `GeminiBlockedException` → 재시도 없이 `FALLBACK_BLOCKED`.
  - 형식 이상 → `GeminiUnavailableException` → `FALLBACK_ERROR`.
- 일시 오류(`ResourceAccessException`, 429, 5xx)만 최대 3회, backoff 0/0.5/1.5s 재시도.
- `InvestmentReportService`: `## ` 마커가 없으면(=fallback 메시지) 그 메시지를 섹션에 그대로 노출
  (구조화 출력 전면 개편은 스테이지 3).

### 검증
- `GeminiResponseParseTest` (순수 단위 테스트, 5케이스): 정상/parts 빈 SAFETY/content 없음/
  candidates 없음·null/promptFeedback 차단.

---

## ⑤ 요약

| 항목 | Before | After |
| --- | --- | --- |
| 요청당 AI 서버 호출 | 2회 (더미 1 + 실제 1) | 1회 |
| `/stocks/price-range` 미래 시세 | 이후 5영업일 노출 | 기준일 이하만 |
| 시세 히스토리 쿼리 | 종목당 3~4회 | 종목당 1~2회 |
| 거래 가격 검증 | 없음 (임의 가격 통과) | 서버 시세 ±1%, 체결은 서버가 |
| Gemini 안전필터 차단 | NPE → `"...오류: null"` | `FALLBACK_BLOCKED` |
| Gemini 일시 오류 | 즉시 실패 | 3회 재시도(backoff) |
| Gemini 타임아웃 | 없음 | connect 5s / read 20s |

## ⑥ 회고 / 자소서·면접 문장 초안

- **클라이언트를 믿지 않는다**: 거래 가격을 클라이언트 값 그대로 받던 것을, 서버 시세를 기준으로
  허용 오차만 검증하고 체결은 서버가로 강제하도록 재설계. "완전 일치 검증이 실패해서 검증을 껐다"는
  선택을, 슬리피지 허용 범위를 둔 검증으로 되살림.
- **정답을 미리 보여주지 않는다**: 학습 시뮬레이션에서 판단 시점 이후 주가를 API 가 그대로 주고 있었음.
  "노출 가능 데이터의 상한 = 판단 시점" 을 조회 레이어에서 강제.
- **외부 LLM 은 실패한다**: 안전 필터 차단·응답 지연·일시 오류를 각각 다르게 처리(즉시 fallback vs
  재시도). 캐스팅 파싱 → 방어적 파싱 + `finishReason` 기반 분기.
- 예상 질문: "가격 검증을 왜 1%로 뒀나?" → "정상 흐름에서 FE 와 서버는 같은 일봉 종가를 쓰므로 거의
  0%. 1% 는 라운딩·엣지 케이스 여유이고, 스테일/하드코딩 값(10~50% 오차)은 확실히 걸러진다."
