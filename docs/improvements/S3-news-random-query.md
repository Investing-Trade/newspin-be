# S3. 뉴스 랜덤 조회 쿼리 최적화 (I-1)

- **분류**: 개선 (성능)
- **이슈 ID**: I-1
- **브랜치**: `perf/news-random-query`
- **상태**: ✅ 구현 완료
- **기간**: 2026-09-09

---

## ① 문제

`GET /news/random` (미학습 뉴스 1건 랜덤 반환) 이 매 호출마다:

```java
List<Long> learnedIds = userNewsProgressRepository.findLearnedNewsIdsByUserId(userId);
List<NewsArticle> allNews = newsArticleRepository.findAll();          // 뉴스 2,528건 전부 로딩
List<NewsArticle> unlearned = allNews.stream()
        .filter(n -> !learnedIds.contains(n.getNewsId()))             // List.contains → O(n·m)
        .toList();
NewsArticle picked = unlearned.get(random.nextInt(unlearned.size()));
return NewsResponse.from(picked);                                    // 결국 1건만 사용
```

시드 기준 뉴스 2,528건(각 TEXT 본문 포함)을 전부 엔티티로 하이드레이션하고, 그중 1건만 응답.

## ② 계측 (Before)

로컬, 신규 유저, `curl` 60회:

| p50 | p95 | mean | 엔티티 로드/요청 |
| --- | --- | --- | --- |
| 1,675 ms | 3,353 ms | 1,918 ms | 2,528 |

원본: `docs/benchmarks/results/2026-09-09-news-random.md`

## ③ 조치

- `NewsArticleRepository.countUnlearnedByUser` — `SELECT COUNT(n) ... WHERE NOT EXISTS (progress)`
- `NewsArticleRepository.findUnlearnedByUser(userId, Pageable)` — 같은 `NOT EXISTS` + `ORDER BY newsId`,
  `PageRequest.of(offset, 1)` 로 LIMIT/OFFSET
- `getRandomUnlearnedNews`: count → `random.nextInt(count)` → offset 1건 조회.
  전부 학습한 경우에만 `resetUserProgress` 후 전체에서 뽑음.
- `getRandomUnlearnedNews` 를 `@Transactional`(쓰기)로 — 리셋 경로가 같은 트랜잭션에서 동작해야 함
  (기존엔 `readOnly` + self-invocation 으로 `resetUserProgress` 의 `@Transactional` 이 무시되던 상태).
- 사용처가 사라진 `findLearnedNewsIdsByUserId` 제거. `deleteAllByUserId` 에 `clearAutomatically = true`.

## ④ 검증 (After)

| 지표 | Before | After | 변화 |
| --- | --- | --- | --- |
| p50 | 1,675 ms | 101 ms | -94% |
| p95 | 3,353 ms | 132 ms | -96% |
| mean | 1,918 ms | 102 ms | -95% |
| 엔티티 로드/요청 | 2,528 | 1 | |

(After 의 ~100ms 는 curl 서브프로세스·인증 필터 오버헤드. 서버 쿼리 자체는 한 자릿수 ms.)

- `NewsRandomQueryTest` (CI): `entityLoadCount <= 1`, `prepareStatementCount <= 3`.

## ⑤ 회고

- "결국 1건만 쓰는데 전체를 로딩"은 데이터가 적을 때 안 보이다가 시드가 커지면서 드러난다.
  `findAll()` 는 리스트가 확실히 작을 때만.
- `@Transactional(readOnly=true)` + 같은 클래스 메서드 self-invocation → 안쪽 `@Transactional` 이
  적용 안 됨. 조회 위주 메서드에 쓰기 경로가 섞여 있으면 경계를 다시 봐야 한다.
- 남은 과제: `ORDER BY newsId` + OFFSET 은 offset 이 클수록 스캔이 늘어난다. 2,528건에선 무의미하지만
  수십만 건이면 keyset(마지막 id 이후) 방식이나 랜덤 id 샘플링을 고려.
