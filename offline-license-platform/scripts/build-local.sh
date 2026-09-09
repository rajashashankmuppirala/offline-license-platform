#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec mvn -f "$SCRIPT_DIR/../pom.xml" clean verify "$@"
