#!/usr/bin/env bash
# Use a local JDK 21 + Maven if present; otherwise Maven in Docker.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -x "${JAVA_HOME:-$HOME/sdk/jdk-21}/bin/java" ] && [ -x "${HOME}/sdk/maven/bin/mvn" ]; then
  export JAVA_HOME="${JAVA_HOME:-$HOME/sdk/jdk-21}"
  exec "${HOME}/sdk/maven/bin/mvn" -f "${ROOT}/backend/pom.xml" "$@"
fi
docker run --rm \
  --network host \
  -v "${ROOT}:/src" \
  -v flowgate-m2:/root/.m2 \
  -w /src/backend \
  maven:3.9.9-eclipse-temurin-21 \
  mvn "$@"
