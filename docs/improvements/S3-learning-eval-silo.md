# S3. 학습-평가 사일로 완화 (S-2)

- **분류**: 개선 (설계 / 학습 피드백 품질)
- **이슈 ID**: S-2
- **브랜치**: `feat/report-judgment-accuracy`
- **상태**: ✅ 구현 완료 (BE 입력만 확장, 응답 형태 불변)
- **기간**: 2026-09-10

---

## ① 문제

두 학습 기능이 데이터상 끊겨 있었다.

- **뉴스 판단 퀴즈** (`POST /news/{id}/analyze`): 사용자가 개별 뉴스의 호재/악재를 판단 →
  newspin-ai 정답과 비교해 `isCorrect` 를 응답. 하지만 **그 결과를 저장하지 않음.**
  `UserNewsProgress` 는 "학습했다"(`learnedAt`)만 기록.
- **투자 리포트** (`GET /simulation/sessions/{id}/report`): `EventStockImpact`(정답지)가
  대형 이벤트 3건에만 존재. 2,500여 건 뉴스 중 대부분의 일상 판단은 리포트에서 채점 대상이 아님.

결과적으로 리포트의 "뉴스 대응" 평가는 이벤트 3건 대응에만 근거했고, 사용자가 평소
호재/악재를 얼마나 잘 구분하는지는 최종 피드백에 전혀 반영되지 않았다.

## ② 조치

**개별 뉴스 판단의 정오 이력을 영속화하고, 리포트 프롬프트 입력에 판단 정확도를 추가.**
응답 스키마는 그대로 두고 (FE 변경 없음) LLM 입력만 넓힌다.

- `V3__user_news_judgment.sql` — `user_news_progress` 에 컬럼 추가:
  `user_sentiment`, `ai_sentiment` (`ENUM('NEGATIVE','NEUTRAL','POSITIVE')`, nullable), `judged_at`.
- `UserNewsProgress` — `withJudgment(...)` 팩토리, `recordJudgment(user, ai)` (재판단 시 갱신),
  `isJudged()` / `isCorrect()`.
- `NewsService.markNewsAsLearned` → `recordNewsJudgment(userId, newsId, userSentiment, aiSentiment)`.
  기존 행이 있으면 최신 판단으로 갱신, 없으면 판단까지 담아 생성.
- `AIService.analyzeUserJudgment` 가 이미 계산 중이던 `aiSentiment` 를 그대로 넘긴다 (추가 호출 없음).
- `UserNewsProgressRepository.findJudgedByUserInPeriod(userId, start, end)` —
  세션 기간에 발행된 뉴스에 대한 판단 이력 (`JOIN FETCH` 로 N+1 방지).
- `ReportGenerator.buildPrompt` 에 `appendJudgmentAccuracy(...)` 섹션:
  - 판단한 뉴스 N건 중 정답 M건 (정확도 %)
  - 감성별 분해 (호재로 판단한 것 중 몇 건 정답 …)
  - 대표 오답 최대 5건 (날짜·제목·사용자 판단·실제)
  - `newsResponseAnalysis` 지시문을 "이벤트 대응 + 일상 뉴스 감성 판단 정확도"로 확장.

## ③ 검증

- `NewsJudgmentAccuracyTest` (CI):
  - `recordNewsJudgment` 2건(정답1/오답1) → `findJudgedByUserInPeriod` 로 정확히 채점.
  - 같은 뉴스 재판단 → 행 수 그대로, 최신 판단으로 갱신.
- `AiAnalysisResilienceTest` — AI 장애 시 판단이 기록되지 않는다(기존 계약) 그대로 통과.
- 컨텍스트 로딩 = `V3` 마이그레이션이 엔티티와 일치(`ddl-auto: validate`).

## ④ FE 영향

**없음.** `/news/{id}/analyze` 요청·응답, 리포트 응답 모두 그대로. 판단 이력은 서버가
자동 축적하고 리포트가 내부적으로 활용한다.

## ⑤ 회고

- "정답지 = `EventStockImpact` 3건" 이라는 암묵적 제약이 리포트 품질의 상한이었다.
  사용자가 매 판단마다 남기는 정오 데이터가 이미 흐르고 있었는데 버려지고 있었을 뿐.
- 판단 시점의 `aiSentiment` 를 정답 근거로 저장 — 뉴스의 "고정 정답"(`NewsArticle.sentiment`)이
  아니라 그때 사용자가 비교당한 값이라, 피드백의 일관성이 유지된다.
- 남은 여지: 정확도를 리포트 응답 필드로도 노출하면 FE 가 별도 화면을 만들 수 있다.
  이번엔 "응답 형태 불변" 원칙으로 입력만 확장. S-3(정답지-시세 정합성)은 데이터 파이프라인
  영역이라 범위 밖 유지.
