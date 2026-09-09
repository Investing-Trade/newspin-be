# NewsPin 백엔드 고도화 기록

`newspin-be`(Spring Boot / Java)를 자소서·면접에서 설명 가능한 수준으로 끌어올리는 개선 작업의 전체 기록입니다.
AI 레포(`newspin-ai`)는 원칙적으로 건드리지 않고, BE 중심으로 필요 시 FE만 최소 수정합니다.

## 문서 지도

| 문서 | 내용 |
| --- | --- |
| [00-baseline/01-architecture.md](00-baseline/01-architecture.md) | 착수 시점(2026-09) 아키텍처 스냅샷 |
| [00-baseline/02-known-issues.md](00-baseline/02-known-issues.md) | 발견된 문제 인벤토리 (심각 / 위험 / 개선) |
| [00-baseline/03-improvement-roadmap.md](00-baseline/03-improvement-roadmap.md) | 실행 순서와 단계별 목표 |
| [improvements/_TEMPLATE.md](improvements/_TEMPLATE.md) | 개선 항목 1건당 문서 템플릿 |
| [adr/](adr/) | 아키텍처 의사결정 기록(ADR) |
| [benchmarks/](benchmarks/) | 부하 테스트 스크립트와 측정 원본 |

## 작업 원칙

- **1 항목 = 1 브랜치 = 1 PR = 1 문서.** 브랜치 접두사: `feat/ fix/ perf/ refactor/ test/ chore/`
- **측정 없이 최적화 없다.** 성능 항목은 착수 전 기준선 수치를 먼저 남긴다.
- **회귀 방지.** 손대기 전 해당 영역에 특성화 테스트(characterization test)를 깔고 시작한다.
- **리팩토링 커밋과 동작 변경 커밋을 분리한다.**
- `newspin-ai`는 코드 변경 없음. BE↔AI 계약이 걸리면 BE에서 흡수한다.

## 진행 현황

우선순위 정책: **심각(신뢰도 훼손) → 위험(장애·데이터 오염 가능) → 개선(성능·구조)**

번호는 진행 순서(로드맵 스테이지 순). 상세는 [로드맵](00-baseline/03-improvement-roadmap.md)·[문제 인벤토리](00-baseline/02-known-issues.md).

| 순서 | 항목 | 이슈 | 분류 | 상태 | 문서 · PR |
| --- | --- | --- | --- | --- | --- |
| S0 | 빌드 툴체인·docs 체계 | I-15 | 인프라 | ✅ | [00-setup](improvements/00-project-setup.md) |
| S0 | docker-compose + dev 프로필 + application-local.example | - | 인프라 | ✅ | [S0](improvements/S0-groundwork.md) |
| S0 | Flyway baseline + 시드 데이터 이관 (SeedRunner) | C-2 | 인프라 | ✅ | [S0](improvements/S0-groundwork.md) |
| S0 | Testcontainers 통합테스트 + lookahead 특성화 테스트 | I-13 | 인프라 | ✅ (CI 검증) | [S0](improvements/S0-groundwork.md) |
| S0 | 관측성 (Actuator·Micrometer·traceId·구조화 로깅) | I-12 | 인프라 | ✅ | [S0](improvements/S0-groundwork.md) |
| S0 | k6 부하 스크립트 (기준선 수치는 각 성능 항목에서) | - | 인프라 | ✅ | [k6](benchmarks/k6/) |
| S0 | GitHub Actions CI (build + test) | I-14 | 인프라 | ✅ | [ci.yml](../.github/workflows/ci.yml) |
| 1 | AIService 하드코딩 테스트 호출 제거 | C-1 | 심각 | ✅ | [S1](improvements/S1-critical.md) |
| 2 | 로컬 원큐 실행 + 시드 데이터 검증 | C-2 | 심각 | ✅ | S0 에서 완료 |
| 3 | 미래 시세 유출 차단 (lookahead) | C-5, S-1 | 심각 | ✅ | [S1](improvements/S1-critical.md) |
| 4 | 모의투자 가격 검증 재설계 | C-3 | 심각 | ✅ | [S1](improvements/S1-critical.md) |
| 5 | GeminiService 응답 파싱 방어 + 재시도 | C-4 | 심각 | ✅ | [S1](improvements/S1-critical.md) |
| 6 | 트랜잭션 경계 분리 (외부 호출 축출) | R-1 | 위험 | ✅ | [S2-A](improvements/S2-resilience.md) |
| 7 | AI 연동 회복탄력성 (Resilience4j) | R-2 | 위험 | ✅ | [S2-A](improvements/S2-resilience.md) |
| 8 | AssetHistory 동시성 처리 | R-3 | 위험 | ✅ | [S2-B](improvements/S2-concurrency.md) |
| 9 | 세션 거래 동시성 (잔고 lost update) | R-4 | 위험 | ✅ | [S2-B](improvements/S2-concurrency.md) |
| 10 | 시세 결측 조용한 0 처리 → 관측 가능화 | R-5 | 위험 | ⬜ | - |
| 11 | 프로필/보안 설정 분리 (CORS·ddl-auto·로깅) | R-6, I-8 | 위험 | ⬜ | - |
| 12 | 뉴스 랜덤 조회 쿼리 최적화 | I-1 | 개선 | ⬜ | - |
| 13 | StockPrice 반복 조회 배치화 | I-2 | 개선 | ⬜ | - |
| 14 | Redis 캐시 계층 실제 도입 | I-3 | 개선 | ⬜ | - |
| 15 | HTTP 클라이언트 통일 (RestClient 빈) | I-5 | 개선 | ⬜ | - |
| 16 | InvestmentReport 구조화 출력 (JSON 스키마) | I-4 | 개선 | ⬜ | - |
| 17 | 권한 체계 정비 (ROLE_ 접두사 버그) | I-6 | 개선 | ⬜ | - |
| 18 | Redis refresh token 키 (멀티 디바이스) | I-7 | 개선 | ⬜ | - |
| 19 | SimulationSession 데드코드 정리·로직 일원화 | I-9 | 개선 | ⬜ | - |
| 20 | 목록 API 페이지네이션 | I-10 | 개선 | ⬜ | - |
| 21 | 리포트 생성 비동기화 | I-11 | 개선 | ⬜ | - |
| 22 | 학습-평가 사일로 완화 | S-2 | 개선 | ⬜ | - |

상태 범례: ⬜ 대기 · 🚧 진행 중 · ✅ 완료 · ⏸ 보류

**범위 밖(기록만)**: S-3 정답지-시세 정합성(데이터 파이프라인 영역), 감성 점수 스케일 등 문서-코드 표기 차이.
