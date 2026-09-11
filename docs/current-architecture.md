# 현재 아키텍처 스냅샷 (2026-09-11, 스테이지 0~4 완료 시점)

[00-baseline/01-architecture.md](00-baseline/01-architecture.md)(착수 시점, 2026-09-09)의 After 버전.
바뀐 부분 위주로 기록하고, 그대로인 부분은 baseline 문서를 참고하도록 생략한다.

## 런타임 구성

```
[newspin-fe : React/Vite, localhost:5173]
        │  REST + JWT(Bearer)
        ▼
[newspin-be : Spring Boot 4.0.1, Java 25]
   ├── MySQL         — JPA/Hibernate, ddl-auto: validate(prod) / update(dev)
   │                    Flyway 로 스키마 버전 관리 (V1 baseline, V2 investment_report, V3 user_news_judgment)
   ├── Redis         — JWT refresh token(디바이스별 키) / 이메일 인증코드 / 시세 조회 캐시(@Cacheable, TTL)
   │                    RedisConnectionFactory 는 Spring Boot 오토컨피그 사용 (I-18)
   ├── Gemini API    — GeminiService, RestClient 기반. JSON 스키마 강제 + 재시도(backoff) + 서킷 없음(외부 API 특성상 재시도/폴백으로 대응)
   │                    비동기 호출(ReportGenerator, @Async) — 리포트 생성은 응답 경로 밖
   ├── Resilience4j  — newspin-ai 호출 경로에 circuitbreaker + retry (aiAnalysis)
   ├── Actuator/Micrometer — /actuator/health, /actuator/prometheus 공개. MDC traceId 전 요청에 부여
   └── newspin-ai    — RestClient(aiAnalysisRestClient) 재사용, connect 3s / read 15s, 회복탄력성 적용
                          │
                          ▼
                  [newspin-ai : FastAPI, Python 3.11, uvicorn workers=1]
                     (변경 없음 — 이번 라운드에서 코드 미접촉)
```

## 도메인 패키지

착수 시점과 동일한 구조(`domain.*`, `global.*`) 유지. 신규 추가:
- `domain.ai.report.domain` — `InvestmentReport`(엔티티), `ReportStatus`(enum)
- `domain.ai.report.repository` — `InvestmentReportRepository`
- `domain.ai.report.service.ReportGenerator` — Gemini 비동기 호출 전담(트랜잭션 분리)
- `global.config.AsyncConfig`, `global.config.ResilienceConfig`

계층 네이밍 불일치(`application` vs `service`, `presentation` vs `controller`)는 그대로 남아있다 —
동작에 영향 없는 스타일 이슈라 이번 라운드 범위에서 제외.

## 엔드포인트 인벤토리 (21개, 변경분만)

| 메서드 | 경로 | 착수 시점 대비 변경 |
| --- | --- | --- |
| GET | `/simulation/sessions`, `/simulation/sessions/{id}/trades` | 전량 배열 → `PageResponse`(page/size/totalElements/hasNext), 기본 20·최대 100 (I-10) |
| GET | `/simulation/sessions/{id}/report` | 동기 1회 호출 → 비동기. 응답에 `status`(GENERATING/READY/FAILED), 최초 요청 202, 완료 후 200 (I-11) |
| GET | `/stocks/{code}/price-range`, `/stocks/price-range` | 이후 5영업일 lookahead 제거, 기준일 이하만 반환 (C-5). Redis 캐시 적용 (I-3) |
| POST | `/simulation/sessions/{id}/trades` | 클라이언트 가격 무시하던 것 → 서버 시세 ±1% 검증 후 체결 (C-3) |
| POST | `/news/{newsId}/analyze` | 판단 정오를 `user_news_progress` 에 영속화, 리포트 정확도 산정에 사용 (S-2) |

그 외 엔드포인트는 경로·요청/응답 형태 변경 없음. 전체 목록은 Swagger(`/swagger-ui/index.html`, 어노테이션 정리 완료 — S4).

## 핵심 플로우 (변경분)

### ① 뉴스 감정 판단 학습
- 더미 테스트 호출 제거 (C-1).
- `AIService` 트랜잭션에서 외부 HTTP(newspin-ai, Gemini)를 축출, Resilience4j 적용 (R-1, R-2).
- 판단 결과(`aiSentiment`)를 `UserNewsProgress` 에 영속화 (S-2) — 재판단 시 최신값으로 갱신.

### ② 투자 리포트
1. `GET /report` 호출 즉시 규칙 기반 요약(자산/거래 수)을 계산해 반환. 리포트 행이 없으면
   `GENERATING` 으로 생성하고 커밋 후 `ReportGenerator.generateAsync`(`@Async`) 트리거.
2. `ReportGenerator` 가 별도 트랜잭션에서 프롬프트 구성(트레이드/자산이력/이벤트뉴스 +
   S-2 판단 정확도 섹션) → `GeminiService.generateJson`(스키마 강제) → `markReady`/`markFailed`.
3. 후속 `GET /report` 는 저장된 `InvestmentReport` 행을 즉시 반환(재호출 시 Gemini 재호출 없음).
4. `FAILED` 상태는 다음 `GET` 요청에서 자동 재시도.

## 인증/보안 현황 (변경분)

- `CustomUserDetails.getAuthorities()` → `"ROLE_USER"` (I-6). `hasRole("ADMIN")` 등 도달 불가 매처 제거.
- Redis refresh token 키 = `refresh:{email}:{md5(token)}` (jti 포함) — 멀티 디바이스 독립 세션 (I-7).
- CORS origin: `newspin.cors.allowed-origins` 환경변수 (프로필별 분리, I-8).
- `ddl-auto`: dev/local `update`, prod `validate` (R-6).
- Swagger 문서의 `SecurityRequirement` 가 `SecurityConfig.PERMIT_ALL_PATTERNS` 와 1:1 대응 (S4).
- 그대로: STATELESS, CSRF/폼로그인 비활성, JWT access 24h/refresh 3d, jjwt 0.11.5(I-17, 미해결 — deprecated API 이지만 동작 영향 없음).

## 영속성 현황 (변경분)

- Flyway 도입 — `ddl-auto: validate` 로 스키마 드리프트 차단 (C-2, R-6).
- 신규 테이블: `investment_report`(I-11), `user_news_progress` 확장 컬럼(S-2).
- 반복 조회 → 배치 조회 (I-1 뉴스, I-2 시세) — 상세는 [04-final-summary.md](00-baseline/04-final-summary.md).
- `AssetHistory`/`SimulationSession` 동시성: 비관적 락 (R-3, R-4).
- 시세 결측: `exact`/`fallback`/`missing` 3분류 + 메트릭 (R-5). 거래는 시세 없으면 거부.

## 테스트/빌드/배포 현황 (변경분)

- 테스트: Testcontainers(MySQL 8.4 + Redis 7.4) 통합 테스트 + 특성화 테스트 다수. CI(GitHub Actions)에서 상시 실행 (I-13, I-14).
- 빌드: Gradle 9.2.1, `foojay-resolver-convention` 으로 JDK 25 툴체인 자동 프로비저닝. `Dockerfile`/`build.gradle` JDK 버전 통일 (I-15).
- 시드 데이터: `SeedRunner` 가 git 안의 데이터를 원큐 적재 (C-2).
- 관측성: Actuator + Micrometer(Prometheus) + MDC traceId (I-12).
- 남은 과제(범위 밖): I-16(validation 에러 표준화), I-17(jjwt 마이그레이션), S-3(정답지-시세 정합성 파이프라인).
