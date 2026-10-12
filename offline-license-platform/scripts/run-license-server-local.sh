#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$ROOT/.local/license-server/data" "$ROOT/.local/license-server/identity" "$ROOT/.local/pids"
PID_FILE="$ROOT/.local/pids/license-server.pid"
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "License Server is already running with PID $(cat "$PID_FILE"); stop it before starting another demo." >&2
  exit 73
fi
: "${LICENSE_SERVER_ADMIN_TOKEN:?Set LICENSE_SERVER_ADMIN_TOKEN to a non-empty local secret}"
: "${LICENSE_SERVER_VENDOR_PUBLIC_KEY_FILE:?Set LICENSE_SERVER_VENDOR_PUBLIC_KEY_FILE to the vendor public key}"
export LICENSE_SERVER_DATA_FILE=${LICENSE_SERVER_DATA_FILE:-"$ROOT/.local/license-server/data/license-server.db"}
export LICENSE_SERVER_IDENTITY_DIR=${LICENSE_SERVER_IDENTITY_DIR:-"$ROOT/.local/license-server/identity"}
export LICENSE_SERVER_CHECKPOINT_FILE=${LICENSE_SERVER_CHECKPOINT_FILE:-"$ROOT/.local/license-server/identity/trusted-time.checkpoint"}
JAR="$ROOT/license-server/target/license-server-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then echo "Build first: mvn -DskipTests package" >&2; exit 2; fi
cd "$ROOT"
nohup java -jar "$JAR" >"$ROOT/.local/license-server/server.log" 2>&1 &
echo $! >"$PID_FILE"
echo "License Server launched with PID $(cat "$PID_FILE"); check readiness and .local/license-server/server.log."
