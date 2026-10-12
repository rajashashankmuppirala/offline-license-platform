#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
for name in license-server sample-product; do
  pidfile="$ROOT/.local/pids/$name.pid"
  if [ -f "$pidfile" ]; then
    pid=$(cat "$pidfile")
    kill "$pid" 2>/dev/null || true
    attempts=0
    while kill -0 "$pid" 2>/dev/null; do
      if [ "$attempts" -ge 15 ]; then
        echo "$name (PID $pid) has not stopped; inspect it before restarting. PID file retained." >&2
        exit 1
      fi
      sleep 1
      attempts=$((attempts + 1))
    done
    rm -f "$pidfile"
    echo "Stopped $name (PID $pid)."
  fi
done
