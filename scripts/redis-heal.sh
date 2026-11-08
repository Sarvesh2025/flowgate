#!/usr/bin/env bash
# Remove the Redis cut toxic.
set -euo pipefail
curl -sS -X DELETE http://localhost:8474/proxies/redis/toxics/cut || true
echo
