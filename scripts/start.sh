#!/bin/sh
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_ROOT"
if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:8000 -sTCP:LISTEN >/dev/null 2>&1; then
  echo 'Port 8000 is occupied. Identify the service before stopping it.' >&2
  exit 1
fi
./mvnw -o -DskipTests package
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17)
fi
exec "$JAVA_HOME/bin/java" -jar java/target/logistics-assistant-0.1.0.jar
