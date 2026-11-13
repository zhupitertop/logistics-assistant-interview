#!/bin/sh
# Project-local, fixed Maven distribution. No automatic downloads.
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  export JAVA_HOME
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo 'JDK 17 is required. Set JAVA_HOME to your JDK 17 installation.' >&2
  exit 1
fi
case "$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)" in
  *\"17.*) ;;
  *) echo 'This project requires JAVA_HOME to point to JDK 17.' >&2; exit 1 ;;
esac
exec "$PROJECT_ROOT/vendor/apache-maven-3.9.16/bin/mvn" -s "$PROJECT_ROOT/.mvn-offline-settings.xml" -gs "$PROJECT_ROOT/.mvn-offline-settings.xml" -f "$PROJECT_ROOT/java/pom.xml" -Dmaven.repo.local="$PROJECT_ROOT/vendor/m2" "$@"
