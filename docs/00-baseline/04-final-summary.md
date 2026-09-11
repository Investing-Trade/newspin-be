# 최종 Before/After 요약 (2026-09-11)

[00-architecture.md](01-architecture.md)(착수 시점 스냅샷)·[02-known-issues.md](02-known-issues.md)(문제 인벤토리) 대비
스테이지 0~4에서 실제로 바뀐 것만 정리한다. 각 행의 근거는 해당 개선 문서를 참고.

## 신뢰도 (심각, C-1~C-5)

| 이슈 | Before | After | 문서 |
| --- | --- | --- | --- |
| C-1 | 요청마다 하드코딩 더미 HTTP 호출 실행(로그만, 결과 미사용) | 제거 | [S1](../improvements/S1-critical.md) |
| C-2 | 시드 데이터 적재 코드가 `.gitignore` 로 git 밖 → clone 후 재현 불가 | Flyway baseline + `SeedRunner` 로 git 안에서 원큐 실행 | [S0](../improvements/S0-groundwork.md) |
| C-3 | `validatePrice` 호출부 주석 처리 — 클라이언트가 보낸 임의 가격으로 체결 | 서버가 세션 현재일 종가 기준 ±1% 검증, 체결가는 서버가 확정 | [S1](../improvements/S1-critical.md) |
| C-4 | Gemini 안전필터 차단 시 NPE, 응답 형식 변형 시 파싱 실패 문자열 노출 | `FALLBACK_BLOCKED`/`FALLBACK_ERROR`, 일시 오류 3회 재시도(backoff), connect 5s/read 20s | [S1](../improvements/S1-critical.md) |
| C-5 / S-1 | `/stocks/price-range` 가 기준일 이후 5영업일 시세까지 반환(lookahead) | 기준일 이하만 반환, 세션 `currentSimulationDate` 상한 강제 | [S1](../improvements/S1-critical.md) |

## 장애·데이터 정합성 (위험, R-1~R-6)

| 이슈 | Before | After | 문서 |
| --- | --- | --- | --- |
| R-1 | 쓰기 트랜잭션 안에서 외부 HTTP(최대 30s)+Gemini 호출 → 커넥션 점유 | 외부 호출을 트랜잭션 밖으로 축출 | [S2-A](../improvements/S2-resilience.md) |
| R-2 | 재시도·서킷브레이커 없음 | Resilience4j: 실패 시 `C902`(~1.5s, 1회 재시도) → 서킷 오픈 후 빠른 실패(~0.6s), Prometheus 메트릭 노출 | [S2-A](../improvements/S2-resilience.md) |
| R-3 | 동시 "다음날 진행" 시 유니크 위반 500 | 비관적 락 — 동시 5건 전부 200, 날짜 중복 없음 | [S2-B](../improvements/S2-concurrency.md) |
| R-4 | 동시 매매 시 잔고 lost update | 비관적 락 — 동시 매수 10건 전부 정확한 잔고, 초과분은 `INSUFFICIENT_CAPITAL` | [S2-B](../improvements/S2-concurrency.md) |
| R-5 | 시세 결측 시 조용히 자산 0원 처리 | exact/fallback/missing 메트릭 노출, 거래는 시세 없으면 `INVALID_TRADE`로 거부 | [S2-C](../improvements/S2-config.md) |
| R-6 / I-8 | `ddl-auto: update`, SQL 로그 상시 on, CORS 하드코딩 | 프로필 분리(`ddl-auto: validate` on prod), CORS `newspin.cors.allowed-origins` 환경변수화, Actuator 노출 축소 | [S2-C](../improvements/S2-config.md) |

## 성능·구조 (개선)

| 이슈 | 지표 | Before | After |
| --- | --- | --- | --- |
| I-1 뉴스 랜덤 조회 | p50 / p95 / 엔티티 로드 | 1,675ms / 3,353ms / 2,528건 | 101ms / 132ms / 1건 (-94% / -96%) |
| I-2 시세 반복 조회 | `/portfolio`, `/next-day` 쿼리 수 | 4~6 | 1 (O(N)→O(1)) |
| I-3 Redis 캐시 | 2번째 호출 쿼리 / 지연 | 2쿼리 · 233ms | 0쿼리 · 114ms |
| I-4 리포트 구조화 출력 | 파싱 방식 | 마커 문자열 `indexOf`/`substring` | `responseSchema` JSON 강제 + `readValue` |
| I-5 HTTP 클라이언트 | 인스턴스화 | 요청마다 `HttpClient.newBuilder()` | `RestClient` 빈 재사용 |
| I-6 권한 체계 | `hasRole` 매칭 | `"USER"`(접두사 없음) → 항상 실패 | `"ROLE_USER"` 부여, 도달 불가 매처 제거 |
| I-7 refresh token | 멀티 디바이스 | 키=email 단독 → 후속 로그인이 이전 토큰 덮어씀 | 키=`email:jti` → 기기별 독립, 재사용 시 `C005` |
| I-9 세션 로직 | `SimulationSession.advanceDay()/reset()` | 서비스와 중복된 데드코드 | 제거, 서비스로 일원화 |
| I-10 목록 API | 응답 형태 | 전량 배열 | `PageResponse`(page/size/totalElements/hasNext), 기본 20·최대 100 |
| I-11 리포트 생성 | 응답 지연 / 트랜잭션 홀딩 | 동기 호출 수 초~15초, `readOnly` 트랜잭션이 Gemini 호출을 물고 있음(I-19) | 202 즉시 응답 + 폴링, Gemini 는 별도 트랜잭션/스레드 |
| I-18 Redis 설정 | 커넥션 팩토리 | `@Value` 로 수동 생성 → 오토컨피그 우회, 환경변수 미설정 시 컨텍스트 로드 실패 | Spring Boot 오토컨피그 사용 |
| S-2 학습-평가 연결 | 리포트 채점 근거 | 대형 이벤트 3건만 | 개별 뉴스 판단 정오(`user_news_progress`) 누적 반영 |
| S4 API 문서 | Swagger 어노테이션 | 0개(엔드포인트 21개 전부) | 전체 `@Tag`/`@Operation`, 인증 요구사항이 `SecurityConfig` 와 1:1 대응 |

## 인프라 (스테이지 0)

| 이슈 | Before | After |
| --- | --- | --- |
| I-12 관측성 | 메트릭·traceId·구조화 로깅 없음 | Actuator + Micrometer(Prometheus) + MDC traceId |
| I-13 테스트 | `NewspinBeApplicationTests`(컨텍스트 로드) 1개, 실질 커버리지 0 | Testcontainers 통합 테스트 + 특성화 테스트 다수(CI 상시 실행) |
| I-14 CI | 없음 | GitHub Actions (build + test) |
| I-15 빌드/배포 | `Dockerfile`(JDK 21) vs `build.gradle`(JDK 25) 불일치 | 통일 |

## 라운드 2 (스테이지 0~4 완료 후 추가 정리)

| 이슈 | Before | After |
| --- | --- | --- |
| I-16 validation 에러 | 필드별 메시지 없이 `"유효성 검사가 실패했습니다."`로 통합, 에러코드는 매직 스트링 `"400"`, `ErrorCode.message`가 `final` 아님 | `ApiResponse.data`에 `{필드명: 메시지}`, 코드 `C002`(`ErrorCode.INVALID_INPUT_VALUE`)로 통일, `message` `final`화 ([round2](../improvements/round2-validation-error-format.md)) |
| I-17 jjwt 버전 | 0.11.5, deprecated API(`parserBuilder`/`setSigningKey`/`SignatureAlgorithm` 등) | 0.12.6, `Jwts.parser().verifyWith()`/`parseSignedClaims()` 등 신규 API로 전면 교체 ([round2](../improvements/round2-jjwt-migration.md)) |

## 범위 밖으로 남긴 것 (의도적)

- **S-3** 정답지(`EventStockImpact`)-시세 정합성 — 시드 데이터 파이프라인 영역, `newspin-be` 코드 개선 범위 밖.
- `newspin-ai` 레포는 원칙대로 코드 변경하지 않음 (BE↔AI 계약 변경은 전부 BE 쪽에서 흡수).

## 실행 방식

문제 22건 + 마감 1건, 총 23개 브랜치/PR/문서. 우선순위 **심각 → 위험 → 개선** 순서로 처리했고,
각 항목은 착수 전 특성화 테스트 또는 기준선 계측을 먼저 남긴 뒤 변경했다(측정 없이 최적화 없음 원칙).
상세 진행표는 [docs/README.md](../README.md), 문제별 우선순위 근거는 [02-known-issues.md](02-known-issues.md) 참고.
