#!/usr/bin/env bash
# Cut Redis through Toxiproxy (gateway must use FLOWGATE_REDIS_PORT=16379).
set -euo pipefail
curl -sS -X POST http://localhost:8474/proxies/redis/toxics \
  -H 'Content-Type: application/json' \
  -d '{"name":"cut","type":"timeout","attributes":{"timeout":1}}'
echo
