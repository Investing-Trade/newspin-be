# 문제 인벤토리 (2026-09-09)

코드로 확인된 문제만 기록한다. 문서(Notion/기획서)와 실제 구현의 표기 차이는 개선 대상이 아니므로 제외.
분류: **심각**(방치 시 프로젝트 신뢰도 훼손) · **위험**(장애·데이터 오염 가능) · **개선**(성능·구조·완성도)

## 심각

| ID | 위치 | 문제 | 영향 |
| --- | --- | --- | --- |
| C-1 | `AIService.analyzeUserJudgment` 앞부분 | 하드코딩된 더미 JSON 테스트 HTTP 호출이 프로덕션 경로에 잔존. 매 요청마다 실행되고 결과는 로그만 남김 | 요청당 불필요한 외부 호출 1회 추가, 지연·부하 증가, 코드 신뢰도 |
| C-2 | `.gitignore` + `DataLoader.java` | 시드 데이터 적재 코드/데이터가 git 제외. clone 후 실행 불가, "적재 완료" 보고와 불일치 | 재현 불가, 성능 측정 불가, 리뷰어 실행 불가 |
| C-3 | `TradeService.executeTrade` | 가격 검증(`validatePrice`) 호출부 주석 처리. 클라이언트가 보낸 임의 가격으로 체결 가능 | 데이터 무결성·신뢰성. "서버가 최종 검증 권한을 갖지 않음" |
| C-4 | `GeminiService.generateContent` / `InvestmentReportService.extractSection` | Gemini 응답을 방어 없이 캐스팅 체인으로 파싱. `finishReason=SAFETY` 등으로 `parts`가 비면 NPE. 리포트는 마커 문자열 검색 파싱이라 형식이 조금만 달라도 실패 문자열 노출 | 외부 요인으로 요청 전체 실패 |
| C-5 | `StockService.getStockPriceHistoryAroundDate` / `getAllStocksPriceHistoryAroundDate`, `GET /stocks/{code}/price-range`, `GET /stocks/price-range` | **미래 정보 유출(lookahead)**. 클라이언트가 보낸 `date` 기준으로 **이후 5영업일치 주가**(종가·변동률 포함)를 그대로 반환. 세션의 `currentSimulationDate`와 대조하지 않음. 유저가 "오늘 뉴스"를 보고 매수를 고민하는 시점에 이 API로 그 종목이 며칠 뒤 오를지/내릴지를 미리 볼 수 있음 | 학습 서비스의 핵심 전제("정답을 미리 보여주지 않는다")가 API 계약 레벨에서 깨짐. 프론트가 실수/의도로 노출하면 서비스 신뢰도 붕괴 |

## 위험

| ID | 위치 | 문제 | 영향 |
| --- | --- | --- | --- |
| R-1 | `AIService` (`@Transactional`) | 쓰기 트랜잭션 안에서 외부 HTTP(최대 30s) + Gemini 호출. DB 커넥션을 그 시간만큼 점유 | 동시 요청 시 HikariCP 풀 고갈 → 전면 장애 전파 |
| R-2 | `AIService` ↔ `newspin-ai`, `GeminiService` | 재시도·타임아웃 정책·서킷브레이커 없음 (HttpClient timeout만 존재) | AI 서버 일시 장애가 그대로 유저 실패로 전파 |
| R-3 | `NextDayService.proceedToNextDay` + `AssetHistory` 유니크 제약 | 동시성 제어 없음. 같은 세션에 중복 호출 시 유니크 위반 예외 | 중복 클릭/재시도에서 500 |
| R-4 | `TradeService.executeBuy/Sell` | 같은 세션 동시 거래 시 `currentCapital` lost update (엔티티 조회→계산→저장, 락 없음) | 잔고 음수/과다, 시뮬레이션 정합성 붕괴 |
| R-5 | `NextDayService.calculateTotalStockValue`, `PortfolioService.getCurrentPrice` | 해당 날짜 시세 없으면 조용히 `BigDecimal.ZERO` 처리 | 자산이 실제보다 낮게 계산되는데 감지 수단 없음 |
| R-6 | `application.yml` | `ddl-auto: update` (운영 스키마 드리프트), `org.hibernate.sql: debug` 상시 (파라미터 바인딩 로그 노출) | 운영 사고·정보 노출 |

## 개선

| ID | 위치 | 문제 | 방향 |
| --- | --- | --- | --- |
| I-1 | `NewsService.getRandomUnlearnedNews` | `findAll()` 후 인메모리 필터링 | DB 레벨 `NOT EXISTS` + 랜덤 |
| I-2 | `NextDayService`, `TradeService`, `PortfolioService` | `findByStockAndPriceDate`를 종목 수만큼 루프 호출 | 기간/일자 범위 배치 조회 후 in-memory 맵 |
| I-3 | Redis | 문서상 캐싱하는데 실제 캐시 계층 없음 | 시세/종목 메타에 `@Cacheable` + TTL/무효화 |
| I-4 | `InvestmentReportService` | 자유 텍스트 마커 파싱 | `response_mime_type: application/json` + 스키마 (AI 레포가 쓰는 패턴 이식) |
| I-5 | `AIService` | 요청마다 `HttpClient.newBuilder()` 새 인스턴스 | `RestClient` 빈(`aiAnalysisRestClient` 이미 존재) 재사용 |
| I-6 | `SecurityConfig` / `CustomUserDetails` | `hasRole` 접두사 불일치로 권한 체크 데드 | `ROLE_` 부여 + `User.role` 필드, 또는 관리자 기능 제거 |
| I-7 | `JwtTokenProvider` + Redis | refresh token 키 = email 단독 | `email:jti` 등으로 디바이스별 세션 |
| I-8 | `SecurityConfig` CORS, `application.yml` | 환경 분리 없음 | `application-prod.yml` + 환경변수 origin |
| I-9 | `SimulationSession.advanceDay()/reset()` | 서비스 레이어와 중복된 데드코드 | 삭제 또는 엔티티로 로직 응집 |
| I-10 | 목록 API (`/simulation/sessions`, `/trades`, 뉴스) | 페이지네이션 없음 | `Slice`/`Page` |
| I-11 | `InvestmentReportController` 등 | 리포트 생성이 동기, 긴 Gemini 호출 | ✅ 해소 — `@Async` + `investment_report` 상태 폴링 ([S3](../improvements/S3-report-async.md)) |
| I-12 | 전역 | 관측성 부재 (메트릭·traceId·구조화 로깅 없음) | Actuator + Micrometer + MDC |
| I-13 | 전역 | 테스트 없음 | Testcontainers 통합 + 단위 |
| I-14 | 전역 | CI 없음 | GitHub Actions build+test |
| I-15 | `Dockerfile` vs `build.gradle` | JDK 21 vs 25 불일치 | 통일 |
| I-16 | `GlobalExceptionHandler` | validation 에러가 필드별 메시지 없이 뭉뚱그림, `ErrorCode.message` 비-final | 응답 표준화 |
| I-17 | jjwt 0.11.5 | deprecated API (`parserBuilder` 등) | 0.12.x 마이그레이션 |
| I-18 | `RedisConfig` | `@Value` 로 `RedisConnectionFactory` 를 직접 생성 → Spring Boot 오토컨피그(`spring.data.redis.*` 바인딩, `@ServiceConnection`, health)를 우회. `spring.data.redis.host` 등에 기본값이 없어 환경변수 미설정 시 컨텍스트 로드 실패 | 커스텀 팩토리 제거, 오토컨피그된 `RedisConnectionFactory` 사용, `RedisTemplate` 커스터마이징만 유지 |
| I-19 | `InvestmentReportService.generateReport` | 클래스 `@Transactional(readOnly=true)` 안에서 Gemini HTTP 호출(수십 초 가능) → R-1 과 같은 커넥션 홀딩 | ✅ 해소 — I-11 에서 Gemini 호출을 별도 스레드/트랜잭션(`ReportGenerator`)으로 분리 ([S3](../improvements/S3-report-async.md)) |

## 구조적 이슈 (설계 레벨)

| ID | 문제 | 방향 | 범위 |
| --- | --- | --- | --- |
| S-1 | **lookahead 방어선이 API에 없음.** C-5는 증상. 근본 원인은 "시뮬레이션에서 유저에게 노출 가능한 데이터의 상한선 = `currentSimulationDate`"라는 규칙이 어디에도 강제되지 않는다는 것. 시세 조회 3개 경로(`StockService`, `NextDayService`, `PortfolioService`, `TradeService`)가 제각기 날짜를 다룸 | 세션 컨텍스트에서 "현재일 이후 데이터는 반환 금지"를 한 곳에서 강제(가드/전용 리포지토리 메서드 `...AndPriceDateLessThanEqual`). 미래 구간 API는 세션에 종속시키고 상한 클램프 | 스테이지 1 (C-5) + 스테이지 2 일부 |
| S-2 | **학습-평가 사일로.** `EventStockImpact`(정답지)가 대형 이벤트 3건에만 존재 → 2,544건 뉴스 중 대부분의 일상 판단은 최종 리포트에서 채점 안 됨. "뉴스 판단 퀴즈"(개별 뉴스 정오)와 "투자 리포트"(이벤트 3건 대응)가 데이터상 연결 안 됨 | 리포트 입력에 `UserNewsProgress` + 개별 뉴스 감성 정오 이력을 포함해 "판단 정확도" 지표를 추가. 정답지는 이벤트 임팩트에만 의존하지 않도록 | 스테이지 3 후보 (항목 신설) |
| S-3 | **정답지-시세 정합성 수작업.** `impactRate`(내러티브 정답)와 `StockPrice`(실제 시세)가 독립 적재 → 실제 주가가 정답과 안 맞으면 시세를 수동 보정해야 앞뒤가 맞음. 이벤트 늘릴 때마다 확장 안 됨 | 시드 파이프라인에서 이벤트 임팩트로부터 해당 구간 시세를 파생 생성(또는 검증)하는 규칙 도입. **데이터 파이프라인 영역이라 이번 BE 개선 범위 밖** — 기록만 | 범위 밖 (기록만) |

## 참고: 건드리지 않는 것

- `newspin-ai` 내부 로직 (규칙 기반 추출, KoELECTRA, validator/summary)
- 감성 점수 스케일, 기술스택 전환 히스토리 등 문서-코드 표기 차이 (구현에는 영향 없음)
