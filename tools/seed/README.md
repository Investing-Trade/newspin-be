# 시드 데이터

`local` / `dev` 프로필에서 빈 DB에 자동 적재된다 (`SeedRunner`, `newspin.seed.enabled=true`).
운영 산출물(jar)에는 포함되지 않는다.

| 파일 | 내용 | 출처 |
| --- | --- | --- |
| `data/stocks.csv` | 종목 20건 (실제 KRX 코드·섹터) | 원본 프로젝트 자료. LG CNS(064400)는 2020 Q1 미상장이라 제외 |
| `data/news.json` | 뉴스 2,544건 (2020-01-01 ~ 03-31, 실제 기사) | 원본 프로젝트 자료. 064400 참조 16건은 적재 시 스킵 |
| `data/prices.csv` | 일봉 1,240행 (20종목 × 62거래일) | `fetch_prices.py` 로 네이버 금융에서 1회 수집 |
| `data/events.json` | 이벤트 10건 + 종목 영향도 107건 | `build_events.py` 로 실제 시세에서 파생 |

## 재생성

```bash
python tools/seed/fetch_prices.py     # prices.csv
python tools/seed/build_events.py      # events.json (prices.csv 필요)
```

## 이벤트 정의

`build_events.py` 상단 `EVENTS` 리스트. 각 이벤트는 코퍼스의 기존 기사(`article_id`)에 `eventType` 을 부여하고,
`impactRate` = 이벤트일 종가 대비 3거래일 뒤 수익률(%) 로 계산한다. |impactRate| ≥ 4% 인 종목만 정답지에 포함.
→ 정답지(`event_stock_impact`)와 실제 시세(`stock_price`)가 구조적으로 일치한다.
