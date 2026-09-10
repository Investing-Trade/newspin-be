# S3. 리포트 생성 비동기화 (I-11)

- **분류**: 개선 (응답성 / 트랜잭션 위생)
- **이슈 ID**: I-11 (겸 I-19 부분 해소)
- **브랜치**: `feat/report-async`
- **상태**: ✅ 구현 완료 (FE 는 이후 폴링 계약 반영)
- **기간**: 2026-09-10

---

## ① 문제

`GET /simulation/sessions/{id}/report` 가 요청 스레드에서 Gemini 호출을 동기로 수행:

- 응답까지 수 초(재시도 시 최대 ~15초). 그동안 커넥션·톰캣 스레드 점유.
- `InvestmentReportService` 가 `@Transactional(readOnly = true)` 안에서 외부 LLM 을 호출 —
  DB 커넥션을 든 채로 네트워크 대기 (I-19). 커넥션 풀 고갈 위험.
- 매 조회마다 Gemini 를 새로 호출 — 같은 세션 리포트를 반복 조회하면 매번 과금·지연.
- 결과가 어디에도 저장되지 않음.

## ② 조치

**규칙 기반 요약은 동기, AI 분석 4개 섹션은 비동기 + 영속화.**

- `investment_report` 테이블 신설 (`V2__investment_report.sql`). 세션당 1행(`uk_investment_report_session`),
  `status ENUM('FAILED','GENERATING','READY')`, 분석 4개 섹션 `TEXT`, `error_message`, `generated_at`.
- `InvestmentReport` 엔티티 — 상태 전이 메서드(`generating` / `restartGeneration` / `markReady` / `markFailed`).
- `InvestmentReportService.getReport()` (얇은 오케스트레이터):
  1. 세션 조회 + 소유자 검증.
  2. 리포트 행이 없으면 `GENERATING` 으로 저장, `FAILED` 면 재시작. → 생성 트리거.
  3. 규칙 기반 요약(최종 자산·수익률·매수/매도 횟수)을 즉시 계산해 반환.
     `status=READY` 일 때만 AI 섹션을 채운다.
- 생성 트리거는 **커밋 이후** 실행 (`TransactionSynchronization.afterCommit`) — 커밋 전에
  비동기 스레드가 돌면 `GENERATING` 행을 못 볼 수 있음.
- `ReportGenerator` (`@Async`) → 자기 프록시로 `generate()`(`@Transactional`) 호출:
  세션 데이터로 프롬프트 구성 → `GeminiService.generateJson()` → `markReady()` / 실패 시 `markFailed()`.
  (`@Async` 메서드 자체엔 트랜잭션을 걸지 않고, 실제 작업 메서드에 경계를 둬 테스트에서 동기 호출 가능.)
- `@EnableAsync` (`AsyncConfig`). 실행기는 Spring Boot 오토컨피그 `applicationTaskExecutor`
  (`spring.task.execution.*`) 사용.
- 컨트롤러: `status=READY` 면 `200 OK`, 아니면 `202 Accepted`.
- `FAILED` 리포트는 다음 조회 때 자동 재시도.

### BE↔FE 계약 변경 (FE 는 이후 반영)

응답에 `status` 필드 추가 (`GENERATING` / `READY` / `FAILED`).
- 첫 조회: `202` + `status=GENERATING`, AI 섹션 `null`. FE 는 요약만 먼저 렌더.
- 폴링(예: 2초 간격) 하다 `200` + `status=READY` 오면 AI 섹션 표시.
- `status=FAILED` 는 "분석 실패, 다시 시도 중" 안내 후 계속 폴링.

## ③ 검증

- `InvestmentReportAsyncTest` (CI):
  - 첫 `getReport` → `status=GENERATING`, AI 섹션 `null`, 요약 필드 채워짐, `investment_report` 행 생성.
  - `ReportGenerator.generate()` → Gemini 실패(더미 키)에도 4개 섹션 fallback 으로 `READY` (C-4 계약 유지).
- 컨텍스트 로딩 = `V2` 마이그레이션이 엔티티와 일치(`ddl-auto: validate`).

## ④ 회고

- 외부 호출을 요청 경로에서 들어내면 (a) 응답 지연, (b) DB 커넥션 점유, (c) 반복 과금이
  한꺼번에 해결된다. 결과를 테이블에 남기니 재조회는 무료.
- `@Async` + `afterCommit` 조합은 "커밋된 데이터를 비동기가 본다"를 보장하는 표준 패턴.
- `@Async` 메서드에 `@Transactional` 을 같이 걸면 자기호출 시 경계가 사라진다.
  작업 로직을 별 메서드로 빼고 자기 프록시(`ObjectProvider`)로 호출 — 테스트에서도 동기로 부를 수 있어 결정적.
- 남은 개선: 동시에 두 요청이 오면 `GENERATING` 행 생성이 경합할 수 있음 →
  현재는 `uk_investment_report_session` 로 두 번째 INSERT 가 실패하고 다음 조회에서 정상화.
  트래픽 늘면 `INSERT ... ON DUPLICATE KEY` 또는 비관 락 고려.
