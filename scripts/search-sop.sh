#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
  echo 'Usage: ./scripts/search-sop.sh "question"' >&2
  exit 1
fi
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_ROOT"
./mvnw -o -DskipTests package >&2
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17)
fi
exec "$JAVA_HOME/bin/java" -jar java/target/logistics-assistant-0.1.0.jar --search-sop "$1"
