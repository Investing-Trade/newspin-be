import http from 'k6/http';
import { check } from 'k6';

// GET /stocks/price-range (전 종목). 항목 13/14(반복 조회·캐시) Before/After 용.
export const options = {
  scenarios: {
    warmup: { executor: 'constant-vus', vus: 5, duration: '30s' },
    measure: { executor: 'constant-vus', vus: 20, duration: '60s', startTime: '30s' },
  },
  thresholds: { http_req_duration: ['p(95)<1500'], checks: ['rate>0.99'] },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const DATES = ['2020-02-03', '2020-02-17', '2020-03-02', '2020-03-16'];

export default function () {
  const date = DATES[Math.floor(Math.random() * DATES.length)];
  const res = http.get(`${BASE}/stocks/price-range?date=${date}`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
}
