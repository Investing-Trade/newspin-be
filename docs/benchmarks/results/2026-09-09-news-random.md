# `/news/random` — I-1 최적화 Before/After

- 날짜: 2026-09-09
- 환경: 로컬 (Docker MySQL 8.4, dev 프로필), 시드 뉴스 2,528건, 신규 유저(진행 이력 없음)
- 측정: `curl` 60회 순차 호출, warm-up 10회 후. (curl 서브프로세스 오버헤드 ~50ms 포함)

| 지표 | Before (`findAll()` 인메모리 필터) | After (count + offset 쿼리) | 변화 |
| --- | --- | --- | --- |
| p50 | 1,675 ms | 101 ms | **-94%** |
| p95 | 3,353 ms | 132 ms | **-96%** |
| mean | 1,918 ms | 102 ms | -95% |
| max | 6,534 ms | 152 ms | -98% |
| NewsArticle 엔티티 로드/요청 | 2,528 | 1 | |

## 원인

`getRandomUnlearnedNews` 가 매 호출마다 `newsArticleRepository.findAll()` 로 뉴스 2,528건
(TEXT 본문 포함)을 전부 엔티티로 하이드레이션한 뒤, `List.contains` 필터로 미학습분만 추림.
실제 응답은 그중 1건.

## 조치

- `countUnlearnedByUser` (COUNT + `NOT EXISTS`) 로 미학습 수 확인
- `random.nextInt(count)` 로 offset 정하고 `findUnlearnedByUser(userId, PageRequest.of(offset, 1))`
  로 1건만 조회
- 전부 학습한 경우에만 진행률 리셋 후 전체에서 뽑음

재현: `docs/benchmarks/k6/news-random.js` 또는 위 curl 루프.
