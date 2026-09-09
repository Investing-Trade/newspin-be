# k6 부하 스크립트

k6 미설치 시 Docker 로 실행한다 (앱은 `dev` 프로필로 먼저 기동).

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev' &

# 토큰 필요 시 로그인 후 export TOKEN=...
docker run --rm -i --network host -e TOKEN grafana/k6 run - < docs/benchmarks/k6/news-random.js
```

결과는 `docs/benchmarks/results/<날짜>-<항목>.md` 로 저장하고 해당 `improvements/NN-*.md` ⑤에 인용.

| 스크립트 | 대상 | 관련 항목 |
| --- | --- | --- |
| `news-random.js` | `GET /news/random` | 12 (뉴스 랜덤 조회 쿼리) |
| `next-day.js` | `POST /simulation/sessions/{id}/next-day` | 13 (StockPrice 반복 조회) |
| `price-range.js` | `GET /stocks/price-range` | 13, 14 (캐시) |
