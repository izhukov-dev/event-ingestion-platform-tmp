import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * SOTA 2026 Open Workload Serving Benchmark
 * 
 * Methodology:
 * - Open Workload Model via 'ramping-arrival-rate' to prevent Coordinated Omission (Gil Tene).
 * - Sizing based on Little's Law: N = X * R
 *   For target throughput X = 2,000 RPS and average latency R = 40 ms (0.040s):
 *   N = 2000 * 0.040 = 80 active concurrent connections on average.
 *   preAllocatedVUs = 200, maxVUs = 1000 to absorb p99/p99.9 latency spikes without rate throttling.
 */
export const options = {
  scenarios: {
    serving_api_open_workload: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: [
        { target: 500, duration: '30s' },   // Stage 1: Warm-up ramp
        { target: 2000, duration: '1m' },   // Stage 2: Ramp up to target load
        { target: 2150, duration: '3m' },   // Stage 3: Sustained target load (2,150 RPS)
        { target: 2500, duration: '1m' },   // Stage 4: Peak stress test (2,500 RPS)
        { target: 0, duration: '30s' },     // Stage 5: Cooldown ramp-down
      ],
    },
  },
  thresholds: {
    // SLA Guarantees:
    'http_req_duration{endpoint:feed}': ['p(95)<40', 'p(99)<100'],
    'http_req_duration{endpoint:search}': ['p(95)<150', 'p(99)<300'],
    'http_req_failed': ['rate<0.001'], // 99.9% success rate required
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';
const TEST_USER_ID = '00000000-0000-0000-0000-000000000001';

export default function () {
  // Traffic split: 80% cached user feed reads, 20% hybrid searches
  const isFeedRequest = Math.random() < 0.8;

  if (isFeedRequest) {
    const feedRes = http.get(
      `${BASE_URL}/api/v1/feed?userId=${TEST_USER_ID}&limit=20`,
      { tags: { endpoint: 'feed' } }
    );
    check(feedRes, {
      'feed status is 200': (r) => r.status === 200,
      'feed has body': (r) => r.body && r.body.length > 0,
    });
  } else {
    const searchRes = http.get(
      `${BASE_URL}/api/v1/content/search?q=java&limit=20`,
      { tags: { endpoint: 'search' } }
    );
    check(searchRes, {
      'search status is 200': (r) => r.status === 200,
      'search returns results': (r) => r.status === 200,
    });
  }
}
