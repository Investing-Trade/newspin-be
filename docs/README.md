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

| # | 항목 | 분류 | 상태 | 문서 | PR |
| --- | --- | --- | --- | --- | --- |
| 00 | 개선 작업 기반 세팅 (빌드 툴체인·docs·측정 골격) | 인프라 | 🚧 진행 중 | [00-setup](improvements/00-project-setup.md) | - |
| 01 | AIService 하드코딩 테스트 호출 제거 | 심각 | ⬜ 대기 | - | - |
| 02 | 로컬 원큐 실행 환경 + 시드 데이터 (docker-compose·Flyway) | 심각 | ⬜ 대기 | - | - |
| 03 | 모의투자 가격 검증 재설계 | 심각 | ⬜ 대기 | - | - |
| 04 | GeminiService 응답 파싱 방어 + 재시도 | 심각 | ⬜ 대기 | - | - |
| 05 | 트랜잭션 경계 분리 (외부 호출을 트랜잭션 밖으로) | 위험 | ⬜ 대기 | - | - |
| 06 | AI 연동 회복탄력성 (Resilience4j) | 위험 | ⬜ 대기 | - | - |
| 07 | AssetHistory 동시성 처리 | 위험 | ⬜ 대기 | - | - |
| 08 | 세션 거래 동시성 (잔고 lost update) | 위험 | ⬜ 대기 | - | - |
| 09 | 시세 결측 조용한 0 처리 → 관측 가능화 | 위험 | ⬜ 대기 | - | - |
| 10 | 뉴스 랜덤 조회 쿼리 최적화 | 개선 | ⬜ 대기 | - | - |
| 11 | StockPrice 반복 조회 배치화 | 개선 | ⬜ 대기 | - | - |
| 12 | Redis 캐시 계층 실제 도입 | 개선 | ⬜ 대기 | - | - |
| 13 | InvestmentReport 구조화 출력 (JSON 스키마) | 개선 | ⬜ 대기 | - | - |
| 14 | HTTP 클라이언트 통일 (RestClient 빈) | 개선 | ⬜ 대기 | - | - |
| 15 | 관측성 (Actuator·Micrometer·traceId) | 인프라 | ⬜ 대기 | - | - |
| 16 | 테스트 스위트 구축 (Testcontainers) | 인프라 | ⬜ 대기 | - | - |
| 17 | CI 파이프라인 (GitHub Actions) | 인프라 | ⬜ 대기 | - | - |
| 18 | 권한 체계 정비 (ROLE_ 접두사 버그) | 개선 | ⬜ 대기 | - | - |
| 19 | Redis refresh token 키 (멀티 디바이스) | 개선 | ⬜ 대기 | - | - |
| 20 | 프로필/보안 설정 분리 (CORS·ddl-auto·로깅) | 개선 | ⬜ 대기 | - | - |
| 21 | SimulationSession 데드코드 정리·로직 일원화 | 개선 | ⬜ 대기 | - | - |
| 22 | 목록 API 페이지네이션 | 개선 | ⬜ 대기 | - | - |
| 23 | 리포트 생성 비동기화 | 개선 | ⬜ 대기 | - | - |

상태 범례: ⬜ 대기 · 🚧 진행 중 · ✅ 완료 · ⏸ 보류
