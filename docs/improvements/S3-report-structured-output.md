# S3. 투자 리포트 구조화 출력 (I-4)

- **분류**: 개선 (생성형 AI 연동)
- **이슈 ID**: I-4 (+ I-19 기록)
- **브랜치**: `feat/report-structured-output`
- **상태**: ✅ 구현 완료 (실 Gemini 검증은 유효 키 필요)
- **기간**: 2026-09-09

---

## ① 문제

`InvestmentReportService` 가 Gemini 에 `## 섹션명` 마커로 4개 섹션을 요청하고, 응답을
`indexOf("## 종합 분석")` / `substring` 문자열 검색으로 파싱. Gemini 가 마커 형식을 조금이라도
다르게 내면(공백, `###`, 번역된 제목 등) → 4개 섹션 모두 `"분석 결과를 파싱할 수 없습니다."`.

## ② 조치

- `GeminiService`:
  - 자유 텍스트용 `generateContent` 와 JSON 강제용 `generateJson(prompt, responseSchema)` 분리.
  - `generateJson` → `generationConfig.responseMimeType = "application/json"` + `responseSchema`,
    temperature 0.2. C-4 의 재시도·차단 처리 로직은 공유(`generate(prompt, config)` 로 추출).
- `InvestmentReportService`:
  - 프롬프트를 "JSON 객체로만 응답" 으로 변경. 4개 필드(`overallAnalysis`,
    `newsResponseAnalysis`, `riskManagementAnalysis`, `improvementSuggestions`) 를 스키마로 강제.
  - 응답을 `ReportSections` record 로 `ObjectMapper.readValue`. 파싱 실패/필수필드 누락이면
    fallback 메시지를 4개 섹션에 채움 (C-4 fallback 유지).
  - `extractSection` 마커 파서 제거.

## ③ 검증

- **로컬 실 Gemini**: 제공받은 API 키가 `CONSUMER_SUSPENDED` (정지) 상태라 실제 구조화 응답은
  확인 못 함. 단, C-4 방어가 동작해 **403 → fallback, 리포트는 200 + 4개 섹션** 정상 반환 확인.
- `InvestmentReportFallbackTest` (CI): Gemini 실패 시 리포트가 500 없이 fallback 섹션으로 반환,
  `"파싱할 수 없습니다"` 문자열 없음.
- 유효 키 확보 시 실제 구조화 응답 + 파싱 실패율 측정 필요 (TODO).

## ④ 회고

- 자유 텍스트 + 정규식/문자열 파싱은 LLM 출력에서 늘 깨진다. `responseMimeType: application/json`
  \+ `responseSchema` 는 모델이 스키마를 지키도록 강제하고, 파싱은 그냥 `readValue` 한 줄.
- 실패 경로(C-4)는 그대로 살려둠 — JSON 모드에서도 안전필터 차단·5xx 는 여전히 발생.
- 남은 과제: `generateReport` 가 `@Transactional(readOnly=true)` 안에서 Gemini 를 호출 (I-19).
  R-1 과 같은 커넥션 홀딩 — 리포트는 호출 빈도가 낮아 후순위로 기록.
