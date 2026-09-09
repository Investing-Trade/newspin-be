"""
2020 Q1 일봉 시세를 네이버 금융에서 1회 수집해 seed/prices.csv 로 저장한다.

- 런타임 의존이 아니라 빌드 시점 데이터 확보용 (결과 CSV는 저장소에 커밋한다).
- 소스: https://api.finance.naver.com/siseJson.naver  (배열: [날짜, 시가, 고가, 저가, 종가, 거래량, 외국인소진율])
- 출력: tools/seed/data/prices.csv  (stockCode,date,open,high,low,close,volume)

usage:  python tools/seed/fetch_prices.py
"""
import csv
import io
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STOCKS_CSV = ROOT / "tools/seed/data/stocks.csv"
OUT_CSV = ROOT / "tools/seed/data/prices.csv"
START, END = "20200101", "20200331"

# 2020 Q1에 상장되지 않았던 종목 (원본 시드에 잘못 포함됨) — stocks.csv 에서도 제외한다.
# 064400 LG CNS: 2025-02 상장. 관련 뉴스 16건은 SeedRunner 가 미등록 종목 참조로 스킵.
EXCLUDE = {"064400"}

URL = (
    "https://api.finance.naver.com/siseJson.naver"
    "?symbol={code}&requestType=1&startTime={start}&endTime={end}&timeframe=day"
)
HEADERS = {"User-Agent": "Mozilla/5.0", "Referer": "https://finance.naver.com/"}
ROW_RE = re.compile(r"\[\s*\"(\d{8})\"\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)")


def read_codes():
    text = STOCKS_CSV.read_text(encoding="utf-8-sig")
    return [row["stockCode"].strip() for row in csv.DictReader(io.StringIO(text))
            if row["stockCode"].strip() not in EXCLUDE]


def fetch(code):
    url = URL.format(code=code, start=START, end=END)
    req = urllib.request.Request(url, headers=HEADERS)
    body = urllib.request.urlopen(req, timeout=15).read()
    try:
        raw = body.decode("utf-8")
    except UnicodeDecodeError:
        raw = body.decode("cp949", errors="replace")
    rows = []
    for m in ROW_RE.finditer(raw):
        d, o, h, low, c, v = m.groups()
        iso = f"{d[0:4]}-{d[4:6]}-{d[6:8]}"
        rows.append((code, iso, int(float(o)), int(float(h)), int(float(low)), int(float(c)), int(float(v))))
    return rows


def main():
    codes = read_codes()
    all_rows = []
    for i, code in enumerate(codes, 1):
        for attempt in range(3):
            try:
                rows = fetch(code)
                break
            except Exception as e:  # noqa: BLE001
                print(f"  {code} retry {attempt + 1}: {e}", file=sys.stderr)
                time.sleep(1.5)
        else:
            print(f"FAILED {code}", file=sys.stderr)
            sys.exit(1)
        if not rows:
            print(f"FAILED {code}: 0 rows", file=sys.stderr)
            sys.exit(1)
        all_rows.extend(rows)
        print(f"[{i:2}/{len(codes)}] {code}: {len(rows)} rows ({rows[0][1]} ~ {rows[-1][1]})")
        time.sleep(0.4)

    OUT_CSV.write_text(
        "stockCode,date,open,high,low,close,volume\n"
        + "\n".join(",".join(map(str, r)) for r in all_rows)
        + "\n",
        encoding="utf-8",
    )
    print(f"\nwrote {len(all_rows)} rows -> {OUT_CSV.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
