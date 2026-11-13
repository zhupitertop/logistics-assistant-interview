#!/bin/sh
# Explicitly online preparation when dependencies change.
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_ROOT"
exec ./mvnw -B package
