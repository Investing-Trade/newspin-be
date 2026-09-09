import http from 'k6/http';
import { check } from 'k6';

// GET /news/random 기준선. 항목 12(findAll() 인메모리 필터링) Before/After 용.
// 인증 필요: TOKEN 환경변수로 Bearer 토큰 전달.
export const options = {
  scenarios: {
    warmup: { executor: 'constant-vus', vus: 5, duration: '30s' },
    measure: { executor: 'constant-vus', vus: 20, duration: '60s', startTime: '30s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<800'],
    checks: ['rate>0.99'],
  },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

export default function () {
  const res = http.get(`${BASE}/news/random`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
}
