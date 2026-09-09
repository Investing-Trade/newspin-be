"""
2020 Q1 코로나 국면 이벤트 10건을 정의하고, 종목별 impactRate 를 실제 시세에서 계산해
tools/seed/data/events.json 을 만든다.

- 이벤트 = 코퍼스에 이미 있는 기사(article_id)에 eventType 을 부여.
- impactRate = 이벤트일 종가 대비 N거래일 뒤 종가 수익률(%). 실제 시세에서 파생하므로
  정답지와 시세가 구조적으로 일치한다 (S-3 완화).
- |impactRate| >= THRESHOLD 인 종목만 정답지에 포함.

usage:  python tools/seed/build_events.py
"""
import csv
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRICES = ROOT / "tools/seed/data/prices.csv"
STOCKS = ROOT / "tools/seed/data/stocks.csv"
OUT = ROOT / "tools/seed/data/events.json"

HORIZON = 3          # 이벤트일 + 3거래일 수익률
THRESHOLD = 4.0      # % — 이보다 작으면 정답지에서 제외

# article_id, 이벤트일, EventType, 요약(로그/문서용)
EVENTS = [
    (606,  "2020-01-22", "PANDEMIC", "우한 폐렴 확산 — 바이오·마스크주 급등, 여행·항공 경계감"),
    (672,  "2020-01-28", "PANDEMIC", "신종 코로나 공포로 코스피 급락"),
    (888,  "2020-01-30", "PANDEMIC", "WHO 국제공중보건비상사태 선언 — 항공 매출 타격 전망"),
    (955,  "2020-02-02", "PANDEMIC", "유통·면세 코로나 충격 — 백화점·마트 임시 휴점 확산"),
    (1442, "2020-02-20", "PANDEMIC", "대구·신천지 집단감염 — 오프라인 유통 직격"),
    (1504, "2020-02-24", "ECONOMIC", "감염병 위기경보 '심각' 격상 — 코스피 2100 붕괴"),
    (1912, "2020-03-12", "PANDEMIC", "WHO 팬데믹 선언 — 전 업종 동반 폭락"),
    (1980, "2020-03-13", "ECONOMIC", "코스피 8% 폭락, 서킷브레이커 발동"),
    (2200, "2020-03-20", "ECONOMIC", "한미 통화스와프 체결 — 시총 상위주 반등"),
    (2259, "2020-03-24", "ECONOMIC", "미 연준 무제한 QE·회사채 매입 — 위험자산 반등"),
]

SECTOR_NOTE = {
    "BIO": "진단·치료제 기대와 방어주 성격",
    "IT_TECH": "지수 등락과 외국인 수급에 연동",
    "RETAIL": "오프라인 점포 휴점과 소비 위축 vs 생필품 수요",
    "TRAVEL": "이동 제한으로 여객·면세 수요 급감",
    "FOOD_FRANCHISE": "외식 수요 위축, 필수 소비 방어",
    "CULTURE_ENTERTAINMENT": "다중이용시설 기피로 관람·공연 타격",
}


def load_prices():
    by_stock = defaultdict(list)
    with open(PRICES, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            by_stock[r["stockCode"]].append((r["date"], int(r["close"])))
    for c in by_stock:
        by_stock[c].sort()
    return by_stock


def load_sectors():
    text = STOCKS.read_text(encoding="utf-8-sig")
    return {r["stockCode"]: (r["stockName"], r["sector"]) for r in csv.DictReader(text.splitlines())}


def ret_after(series, date, horizon):
    dates = [d for d, _ in series]
    if date not in dates:
        # 이벤트일에 거래가 없으면 그 이후 첫 거래일 사용
        later = [d for d in dates if d >= date]
        if not later:
            return None
        date = later[0]
    i = dates.index(date)
    j = min(i + horizon, len(series) - 1)
    base = series[i][1]
    end = series[j][1]
    if base == 0:
        return None
    return round((end - base) / base * 100, 1)


def main():
    prices = load_prices()
    meta = load_sectors()
    out = []
    for article_id, date, etype, summary in EVENTS:
        impacts = []
        for code, series in prices.items():
            r = ret_after(series, date, HORIZON)
            if r is None or abs(r) < THRESHOLD:
                continue
            name, sector = meta[code]
            direction = "상승" if r > 0 else "하락"
            impacts.append({
                "stockCode": code,
                "impactRate": r,
                "impactReason": f"{name}: 이벤트 후 {HORIZON}거래일 {abs(r)}% {direction}. {SECTOR_NOTE.get(sector, '')}".strip(),
            })
        impacts.sort(key=lambda x: x["impactRate"])
        out.append({
            "articleId": article_id,
            "eventType": etype,
            "summary": summary,
            "impacts": impacts,
        })
        print(f"[{article_id}] {date} {etype}: {len(impacts)} impacts  ({summary})")

    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nwrote {OUT.relative_to(ROOT)}  (events={len(out)}, impacts={sum(len(e['impacts']) for e in out)})")


if __name__ == "__main__":
    main()
