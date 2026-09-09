# 부하 테스트 / 측정

성능 개선 항목의 Before/After 근거를 여기에 둔다.

## 도구

- **k6** — HTTP 시나리오 부하. 스크립트는 `k6/*.js`.
- **Hibernate statistics** — 요청당 발생 쿼리 수. 테스트 프로필에서 `generate_statistics: true`.
- **Actuator `/actuator/metrics`** — `http.server.requests`, `hikaricp.connections.*`.

## 측정 규약

- 동일 시드 데이터(Flyway `db/seed`)에서 측정한다.
- 각 시나리오: warm-up 30s → 측정 60s. VU 수는 시나리오 파일에 명시.
- 결과 원본(JSON/요약)은 `results/<날짜>-<항목>.md` 로 저장하고, 해당 `improvements/NN-*.md` 의 ⑤에 표로 인용.

## 시나리오 목록

| 파일 | 대상 | 관련 항목 |
| --- | --- | --- |
| (예정) `k6/news-random.js` | `GET /news/random` | 10 |
| (예정) `k6/next-day.js` | `POST /simulation/sessions/{id}/next-day` | 11, 12 |
| (예정) `k6/analyze.js` | `POST /news/{id}/analyze` | 5, 6 |
