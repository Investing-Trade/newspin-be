# 개선 로드맵

우선순위 정책: **심각 → 위험 → 개선**. 어차피 전부 진행하되, 신뢰도를 먼저 회복한다.
다만 "측정·회귀 방지 없이 손대지 않는다"는 원칙 때문에, 각 항목이 필요로 하는 최소한의 기반은 그 항목 직전에 함께 깐다.

## 스테이지 0 — 작업 기반 (지금)

측정과 회귀 방지가 불가능하면 이후 모든 "개선"이 주관이 된다. 최소 세트만 먼저.

- [x] Gradle 툴체인 자동 프로비저닝 → 로컬에 JDK 25 없이도 빌드 가능 ([#00](../improvements/00-project-setup.md))
- [x] `docs/` 체계 + 템플릿
- [ ] `docker-compose`(MySQL, Redis) + `application-local.yml.example`
- [ ] Flyway 도입, 초기 스키마 baseline + 시드 데이터 이관 (C-2 해소)
- [ ] Testcontainers 기반 통합 테스트 골격 + 두 핵심 플로우 특성화 테스트 (C-1, C-3, C-4 착수 전 안전망)
- [ ] Actuator + Micrometer + MDC traceId + 구조화 로깅 (I-12)
- [ ] k6 부하 스크립트 + 기준선 측정 → `docs/benchmarks/`
- [ ] GitHub Actions CI (build + test) (I-14)

## 스테이지 1 — 심각 (신뢰도 회복)

| 순서 | 항목 | 이슈 | 선행 |
| --- | --- | --- | --- |
| 1 | AIService 하드코딩 테스트 호출 제거 | C-1 | 없음 (즉시) |
| 2 | 로컬 원큐 실행 + 시드 데이터 | C-2 | docker-compose, Flyway |
| 3 | **미래 시세 유출 차단 (lookahead)** — 시세 조회 상한을 세션 `currentSimulationDate`로 클램프, 미래 구간 API는 세션 종속 | C-5, S-1 | 시세 조회 특성화 테스트 |
| 4 | 가격 검증 재설계 (허용 슬리피지 + 서버 권위) | C-3 | 거래 플로우 특성화 테스트 |
| 5 | Gemini 응답 파싱 방어 + fallback + 재시도 | C-4 | AI 호출 특성화 테스트 |

## 스테이지 2 — 위험 (장애·데이터 오염 차단)

| 순서 | 항목 | 이슈 | 메모 |
| --- | --- | --- | --- |
| 6 | 트랜잭션 경계 분리 (외부 호출 축출) | R-1 | 커넥션 풀 고갈 방지 |
| 7 | Resilience4j (retry + circuit breaker + fallback) | R-2 | 장애 전파 차단 |
| 8 | AssetHistory 동시성 | R-3 | 유니크 위반 방지 |
| 9 | 세션 거래 동시성 (잔고 lost update) | R-4 | 비관적 락 |
| 10 | 시세 결측 관측 가능화 (경고 로그 + 메트릭 + 폴백 정책 일관화) | R-5 | 조용한 실패 제거 |
| 11 | 프로필/보안 설정 분리 (`ddl-auto: validate` on prod, SQL 로그 off) | R-6, I-8 | 운영/개발 설정 분리 |

## 스테이지 3 — 개선 (성능·구조)

| 순서 | 항목 | 이슈 | 메모 |
| --- | --- | --- | --- |
| 12 | 뉴스 랜덤 조회 쿼리 최적화 | I-1 | `findAll()` 인메모리 필터 → DB 쿼리. Before/After 측정 |
| 13 | StockPrice 반복 조회 배치화 | I-2 | N회 조회 → 1회 |
| 14 | Redis 캐시 계층 도입 | I-3 | 시세·종목 메타 읽기 캐시 |
| 15 | HTTP 클라이언트 통일 | I-5 | RestClient 빈 재사용 |
| 16 | InvestmentReport 구조화 출력 | I-4 | 마커 파싱 → JSON 스키마 |
| 17 | 권한 체계 정비 | I-6 | `ROLE_` 접두사 |
| 18 | Redis refresh token 키 개선 | I-7 | 멀티 디바이스 세션 |
| 19 | SimulationSession 데드코드 정리 | I-9 | 응집도 |
| 20 | 목록 API 페이지네이션 | I-10 | 확장성 |
| 21 | 리포트 생성 비동기화 | I-11 | 긴 Gemini 호출 분리 |
| 22 | 학습-평가 사일로 완화 (리포트에 개별 뉴스 판단 정확도 반영) | S-2 | 두 학습 기능 연결 |

## 스테이지 4 — 마감

- [x] API 문서(Swagger) 정리 ([S4](../improvements/S4-swagger-docs.md))
- [x] `docs/00-baseline` 대비 최종 Before/After 요약 ([04-final-summary.md](04-final-summary.md))
- [x] 아키텍처 다이어그램 갱신 ([current-architecture.md](../current-architecture.md))
- [ ] README 재작성
