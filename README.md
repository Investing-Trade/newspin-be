# NewsPin Backend (newspin-be)

뉴스를 읽고 호재/악재를 판단하는 학습 기능과, 그 판단을 실제 모의투자로 이어보는 시뮬레이션을
제공하는 백엔드. AI 채점·피드백은 사내 AI 서버(`newspin-ai`)와 Gemini API 두 곳에 연동한다.

## 스택

| 영역 | 구성 |
| --- | --- |
| 런타임 | Spring Boot 4.0.1, Java 25 (Gradle 툴체인, `foojay-resolver` 로 자동 프로비저닝) |
| 저장소 | MySQL 8.4 (Flyway 로 스키마 버전 관리), Redis 7.4 (토큰 저장 + 캐시) |
| 인증 | JWT (access 24h / refresh 3d, 디바이스별 Redis 세션) |
| 외부 연동 | `newspin-ai`(FastAPI, 감정 채점) — Resilience4j(circuitbreaker+retry) 적용 / Gemini API(리포트·피드백 생성) — 재시도 + JSON 스키마 강제 |
| 관측성 | Actuator + Micrometer(Prometheus) + MDC traceId |
| 테스트 | Testcontainers(MySQL+Redis) 통합 테스트, JUnit 5, GitHub Actions CI |

아키텍처 상세는 [docs/current-architecture.md](docs/current-architecture.md) 참고.

## 로컬 실행

```bash
docker compose up -d                      # MySQL(3307), Redis(6380)
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# application-local.yml 에 jwt.secret / gemini.api-key 등 채우기 (git 추적 안 됨)
GEMINI_API_KEY=<키> ./gradlew bootRun --args='--spring.profiles.active=dev'
```

- `dev` 프로필은 `docker-compose.yml` 포트(3307/6380)와 개발용 기본값을 이미 갖고 있어
  `application-local.yml` 없이도 `GEMINI_API_KEY` 환경변수만 있으면 뜬다.
- **Gemini API 키는 절대 커밋하지 않는다.** 환경변수(`GEMINI_API_KEY`) 또는 gitignore 처리된
  `application-local.yml`로만 관리한다.
- 최초 기동 시 Flyway 마이그레이션(`src/main/resources/db/migration/`)이 자동 적용되고,
  `SeedRunner` 가 `tools/seed/data/`(종목·뉴스·이벤트·시세)를 적재한다.

서버 기동 후:
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Actuator: `http://localhost:8080/actuator/health`, `/actuator/prometheus`

## 테스트

```bash
./gradlew test          # 단위 테스트
./gradlew build         # 단위 + Testcontainers 통합 테스트 (Docker 필요)
```

통합 테스트는 Testcontainers 로 MySQL/Redis 컨테이너를 직접 띄운다. CI(GitHub Actions, `ubuntu-latest`)에서
매 PR·push 마다 실행된다(`.github/workflows/ci.yml`).

## 문서

이 저장소의 백엔드 개선 작업 기록은 [`docs/`](docs/README.md)에 있다:

- [docs/00-baseline/](docs/00-baseline/01-architecture.md) — 착수 시점 아키텍처·문제 인벤토리·로드맵
- [docs/current-architecture.md](docs/current-architecture.md) — 현재 아키텍처 스냅샷
- [docs/00-baseline/04-final-summary.md](docs/00-baseline/04-final-summary.md) — 항목별 Before/After 요약
- [docs/improvements/](docs/improvements/) — 항목별 문제→원인→해결→검증 기록 (1 항목 = 1 문서)
- [docs/adr/](docs/adr/) — 아키텍처 의사결정 기록
- [docs/benchmarks/](docs/benchmarks/) — 부하 테스트 스크립트와 측정 원본

## 프로젝트 구조

```
src/main/java/org/gp/newspinbe/
├── domain/
│   ├── user/        인증, 회원, 이메일/비밀번호 인증
│   ├── news/        뉴스 학습 (랜덤 조회, 감정 판단 채점)
│   ├── event/       이벤트-종목 영향도 (리포트 정답지)
│   ├── stock/       종목/시세 (lookahead 차단, Redis 캐시)
│   ├── simulation/  모의투자 세션·거래·포트폴리오
│   └── ai/report/   투자 리포트 (Gemini 비동기 생성)
└── global/
    ├── config/      Security, Redis, Resilience4j, Async, Swagger, RestClient
    ├── security/    JWT 필터/프로바이더
    ├── service/     GeminiService
    ├── exception/   전역 예외 처리
    └── common/      공통 응답 포맷(ApiResponse, PageResponse)
```
