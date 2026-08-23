import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.ADMIN_BASE_URL || 'http://127.0.0.1:8090';
const TOKEN = __ENV.ADMIN_TOKEN || '';

export const options = {
  scenarios: {
    audit_reads: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 30 },
        { duration: '30s', target: 30 },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<100', 'p(99)<200'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/admin/v1/audit-logs?size=20`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  check(response, { 'status is 200': (result) => result.status === 200 });
}
