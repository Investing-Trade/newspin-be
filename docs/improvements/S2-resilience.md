# S2-A. 장애 격리 — 트랜잭션 경계 + 회복탄력성 (R-1, R-2)

- **분류**: 위험
- **이슈 ID**: R-1, R-2 (+ I-5 일부)
- **브랜치**: `feat/stage-2-resilience`
- **PR**: (링크)
- **상태**: ✅ 구현 완료
- **기간**: 2026-09-09

---

## ① 문제

`AIService.analyzeUserJudgment` 하나 안에서:

1. `@Transactional`(쓰기) 로 감싼 채 (클래스 레벨엔 `@Transactional(readOnly=true)`)
2. `newspin-ai` HTTP 호출 (최대 30s) + Gemini HTTP 호출 (재시도 포함)
3. `newsService.markNewsAsLearned` DB 쓰기

→ 외부 호출이 진행되는 내내 HikariCP 커넥션 1개를 점유. 동시 요청이 몰리면 **커넥션 풀 고갈**,
AI 서버 하나의 지연이 전체 API 장애로 전파.

부가:
- `newspin-ai` 호출을 `HttpClient.newBuilder()` 로 **매 요청마다 새로 생성** (I-5).
- 재시도·서킷브레이커 없음 → AI 서버 일시 장애가 그대로 사용자 실패.
- 요청 JSON(기사 본문 포함)을 `log.info` 로 통째로 출력.

## ② 원인 분석

- 트랜잭션 경계가 "메서드 하나" 단위로만 잡혀 있고, 그 안에 I/O 바운드 외부 호출이 들어있음.
- `newsArticle.getRelatedStocks()` 가 LAZY 라서 트랜잭션을 못 걷어냄 (걷으면 `LazyInitializationException`).

## ③ 해결

| 옵션 | 채택 | 근거 |
| --- | --- | --- |
| `@Transactional` 제거 + fetch-join 으로 필요한 연관 미리 로딩 | ✅ | 리포지토리 메서드가 자체 트랜잭션. 외부 호출은 트랜잭션 밖 |
| resilience4j-spring-boot 스타터 (`@Retry`/`@CircuitBreaker` 애노테이션) | ❌ | SB4(Spring 7)에 대한 스타터 호환 리스크 |
| **resilience4j core 를 프로그래밍 방식으로 데코레이트** | ✅ | 자동설정 의존 없음, 명시적, 테스트 쉬움 |

## ④ 구현

- `AIService`: 클래스/메서드 `@Transactional` 전부 제거. `findWithRelatedStocksByNewsId`
  (`@EntityGraph(attributePaths="relatedStocks")`) 로 연관을 미리 로딩. 프롬프트/요청 빌드를
  private 메서드로 분리, 과한 로그 제거.
- `AiAnalysisClient` (신규): `aiAnalysisRestClient`(RestClient 빈, connect 3s/read 15s) 로
  `newspin-ai` 호출. `Retry.decorateSupplier` → `CircuitBreaker.decorateSupplier` 로 감쌈.
  - 서킷 오픈(`CallNotPermittedException`) → `AI_SERVICE_UNAVAILABLE`(C902, 503)
  - 그 외 실패 → `AI_SERVICE_UNAVAILABLE`
- `ResilienceConfig`: `RetryRegistry`/`CircuitBreakerRegistry` 빈. `aiAnalysis` 인스턴스 —
  retry maxAttempts 2 (exp backoff 500ms), CB window 20 / 실패율 50% / open 30s.
  네트워크·429·5xx 만 카운트(4xx 제외). Micrometer 태그드 메트릭 바인딩.
- `RestClientConfig`: `geminiRestClient` 에도 타임아웃(5s/20s).

## ⑤ 검증

로컬 E2E (`newspin-ai` 미기동 = 즉시 연결 거부):

| 시나리오 | 결과 |
| --- | --- |
| 첫 호출 | `C902 AI_SERVICE_UNAVAILABLE`, ~1.5s (1회 재시도), `LazyInitializationException` 없음 |
| 실패 20회 후 | `resilience4j_circuitbreaker_state{name="aiAnalysis",state="open"} 1.0` |
| 서킷 오픈 후 호출 | 빠른 실패 (~0.6s vs ~1.5s), 여전히 `C902` |
| 실패 시 학습 기록 | `user_news_progress` 미기록 (`markNewsAsLearned` 도달 전 실패) |

- 통합 테스트 `AiAnalysisResilienceTest` (CI): 명시적 예외 + 학습 미기록.
- Prometheus: `resilience4j_circuitbreaker_*`, `resilience4j_retry_*` 노출.

## ⑥ 회고 / 자소서·면접 문장 초안

- **CRM 프로젝트와의 연결**: 그때 "마이크로서비스로 자원은 격리했지만 장애 전파 차단(서킷브레이커)까지는
  없었다"를 인지했고, 이번엔 외부 호출을 트랜잭션 밖으로 빼고 Resilience4j 로 보강했다.
- **트랜잭션 경계**: "외부 HTTP 호출이 트랜잭션 안에 있으면, 그 호출이 느려질 때 DB 커넥션이 같이 묶여
  풀이 마른다"는 걸 부하 관점에서 설명하고, `@EntityGraph` 로 필요한 연관만 미리 로딩해 트랜잭션을 걷어냄.
- **관측 가능한 회복탄력성**: 서킷 상태·재시도 횟수를 메트릭으로 노출해 "지금 AI 서버가 죽었는지"를
  대시보드에서 볼 수 있게 함.
- 예상 질문: "왜 애노테이션 방식(resilience4j-spring-boot) 안 썼나?" → "Spring Boot 4 최신이라
  스타터 자동설정 호환이 불확실했다. core 를 직접 데코레이트하는 게 의존도 적고 동작이 명시적이다."
