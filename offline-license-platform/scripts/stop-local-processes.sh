#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
for name in license-server sample-product; do
  pidfile="$ROOT/.local/pids/$name.pid"
  if [ -f "$pidfile" ]; then pid=$(cat "$pidfile"); kill "$pid" 2>/dev/null || true; rm -f "$pidfile"; fi
done
