import http from 'k6/http';
import { check } from 'k6';

// POST /simulation/sessions/{id}/next-day. 항목 13(calculateTotalStockValue 의 종목별 시세 반복 조회) Before/After 용.
// 사전 준비: 세션 1개 생성 후 SESSION_ID 환경변수로 전달. 측정 후 세션은 종료 상태가 되므로
// 반복 측정 시 세션을 새로 만들거나 DB 를 리셋한다.
export const options = {
  scenarios: {
    measure: { executor: 'constant-vus', vus: 1, duration: '30s' },
  },
  thresholds: { http_req_duration: ['p(95)<600'] },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const SESSION_ID = __ENV.SESSION_ID;

export default function () {
  const res = http.post(`${BASE}/simulation/sessions/${SESSION_ID}/next-day`, null, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
}
