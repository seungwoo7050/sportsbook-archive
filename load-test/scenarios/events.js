import http from "k6/http";
import { check } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8085";
const gateStage = __ENV.GATE_STAGE || "measure";

export const options = {
  scenarios: {
    events: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.REQUEST_RATE || 1000),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 200),
      maxVUs: Number(__ENV.MAX_VUS || 500),
      gracefulStop: "5s",
    },
  },
  thresholds: releaseThresholds(),
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export default function () {
  const response = http.get(`${baseUrl}/api/v1/events?size=20`);
  check(response, {
    "events returns a non-empty cursor page": (result) => {
      if (result.status !== 200) return false;
      try {
        return result.json().items.length > 0;
      } catch (_error) {
        return false;
      }
    },
  });
}

function releaseThresholds() {
  if (gateStage !== "measure") return {};
  return {
    http_req_duration: ["p(99)<50"],
    http_req_failed: ["rate<0.001"],
    checks: ["rate>0.999"],
    dropped_iterations: ["count==0"],
  };
}
