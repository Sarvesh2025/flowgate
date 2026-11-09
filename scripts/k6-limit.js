import http from "k6/http";
import { check } from "k6";

export const options = {
  vus: 20,
  duration: "10s",
};

export default function () {
  const res = http.get("http://localhost:8080/api/v1/limited");
  check(res, {
    "limited is 200 or 429": (r) => r.status === 200 || r.status === 429,
  });
}
