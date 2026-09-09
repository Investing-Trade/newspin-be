# S0. 작업 기반 (스테이지 0)

- **분류**: 인프라
- **이슈 ID**: C-2, I-12, I-13, I-14, I-15
- **브랜치**: `chore/stage-0-groundwork`
- **상태**: 🚧 진행 중
- **기간**: 2026-09-09 ~

이후 모든 개선을 Before/After 수치로 말하고, 회귀 없이 진행하기 위한 최소 기반. 코드 동작은 바꾸지 않는다(빌드/설정/테스트/데이터만).

---

## ① 문제

- 빌드 불가 (JDK 25 툴체인 없음) → [#00](00-project-setup.md)에서 해결
- clone 후 실행 불가: 시드 데이터·`DataLoader`가 `.gitignore` 처리되어 저장소에 없음 (C-2)
- 스키마가 `ddl-auto: update`로만 관리 → 이력 없음, 운영 드리프트 위험 (I-15 연장선)
- 테스트 1개, CI 없음, 부하 측정 수단 없음 (I-12, I-13, I-14)

## ② 원인 분석

- `.gitignore`: `application-*.yml`, `src/main/resources/data/`, `global/config/DataLoader.java` 제외
- 시드 데이터 실물: `stocks.csv`(21종목, 실제 KRX)·`news_v3.json`(2,544건, 실제 기사, 2020 Q1)은 확보. **`stock_price`·`event_stock_impact`는 부재.**
- 시세: 2020년 1~3월은 과거 확정 데이터라 `FinanceDataReader`로 1회 수집 후 커밋 가능 (런타임 외부 의존 없음).

## ③ 해결 방안

| 주제 | 결정 | 근거 |
| --- | --- | --- |
| 스키마 관리 | **Flyway** 도입, `ddl-auto: validate` | 이력·재현·리뷰 가능. baseline은 엔티티에서 오프라인 생성 후 정리 |
| 시드 적재 | 프로필(`local`,`dev`) 게이트 `SeedRunner` (멱등, empty-DB에서만) | 원래 `DataLoader` 의도 계승. `docker compose up` → 자동 적재 |
| 시드 원본 | `src/main/resources/seed/` 에 커밋 (`stocks.csv`, `news.json`, `prices.csv`, `events.json`) | 자체 완결·재현. 7MB 감수 |
| 시세 데이터 | `tools/seed/fetch_prices.py` (FinanceDataReader) → `prices.csv` 생성·커밋 | 실제 2020 Q1 일봉. 런타임 의존 X |
| 이벤트 데이터 | 2020 Q1 코로나 국면 이벤트 10건 직접 작성, 실제 주가 이동폭 기반 `impactRate` | 정답지-시세 정합성 확보 (S-3 완화) |
| 로컬 DB | `docker-compose` (mysql:8, redis:7) + `application-local.yml.example` | 기존 네이티브 MySQL 안 건드림 |
| 관측성 | Actuator + Micrometer, MDC `traceId` 필터, 구조화 로깅(JSON, prod) | 이후 장애 격리·성능 항목의 계측 지점 (I-12) |
| 테스트 | Testcontainers(MySQL) 통합 베이스 + 감정판단/리포트 특성화 테스트 | 스테이지 1 착수 전 안전망 (I-13) |
| 부하 측정 | k6 스크립트 3종 + 기준선 측정 | `docs/benchmarks/` (성능 항목 Before) |
| CI | GitHub Actions: build + test (Testcontainers) | I-14 |

## ④ 구현

(진행하며 채움 — 커밋 단위로)

- [x] `chore`: foojay 툴체인, docs 체계 — [#00](00-project-setup.md)
- [ ] `chore`: 시드 원본 편입 + `fetch_prices.py`
- [ ] `feat`: 이벤트 10건 작성 (`events.json`)
- [ ] `feat`: Flyway + baseline 스키마
- [ ] `feat`: `SeedRunner` (프로필 게이트, 멱등)
- [ ] `chore`: docker-compose + application-local.yml.example
- [ ] `feat`: 관측성 (Actuator/Micrometer/MDC)
- [ ] `test`: Testcontainers 베이스 + 특성화 테스트 2건
- [ ] `test`: k6 스크립트 + 기준선 측정
- [ ] `ci`: GitHub Actions

## ⑤ 검증

| 지표 | Before | After |
| --- | --- | --- |
| `git clone` 후 실행 | 불가 (데이터·설정 없음) | `docker compose up` → `./gradlew bootRun` 로 기동 + 시드 |
| 스키마 이력 | 없음 (`update`) | Flyway 마이그레이션 |
| 테스트 | 1 (컨텍스트 로드) | 통합 베이스 + 특성화 2건 |
| CI | 없음 | PR마다 build+test |
| 부하 기준선 | 없음 | `docs/benchmarks/results/` |

## ⑥ 회고 / 자소서·면접 문장 초안

- 배운 점: 레거시 인수인계에서 가장 먼저 확인할 것은 "이거 어떻게 띄우죠?"에 답이 있는가. 데이터·설정·스키마가 재현 가능해야 개선이 논의 가능.
- 자소서 문장(초안): "인수받은 프로젝트가 시드 데이터·실행 설정이 저장소에 없어 재현이 불가능한 상태였다. 실제 과거 시세를 1회 수집해 커밋하고, 정답지 이벤트를 실제 주가 이동폭에 맞춰 재구성했으며, Flyway·컨테이너·프로필 게이트 시더로 `clone → up` 한 번에 동일 환경이 서도록 만들었다."
- 예상 질문: "시드 데이터를 왜 합성이 아니라 실제로 했나?" → "감성 분석 학습이 핵심 기능인데 본문이 합성이면 데모가 안 된다. 시세는 과거 확정치라 수집이 가능했고, 정답지 이벤트만 실제 이동폭에 근거해 작성했다."
