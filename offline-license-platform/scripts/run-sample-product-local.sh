#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PID_DIR="$ROOT/.local/pids"
PID_FILE="$PID_DIR/sample-product.pid"
CLASSPATH_FILE="$ROOT/examples/sample-product/target/runtime-classpath.txt"
MAIN_CLASS=com.enterprise.licensing.example.product.SampleProductApplication

require_env() {
  eval "value=\${$1-}"
  if [ -z "$value" ]; then
    echo "Required environment variable is missing: $1" >&2
    exit 64
  fi
}

require_env LICENSE_SERVER_URL
require_env LICENSE_SERVER_ADMIN_TOKEN
require_env LICENSE_SERVER_ACTIVATION_REQUEST_FILE
require_env SAMPLE_PRODUCT_IDENTITY_FILE

case "$LICENSE_SERVER_URL" in
  http://127.0.0.1:*|http://localhost:*|http://\[::1\]:*) ;;
  *) echo "Local sample runner requires a loopback HTTP LICENSE_SERVER_URL" >&2; exit 64 ;;
esac

mkdir -p "$PID_DIR"
if [ -f "$PID_FILE" ] && kill -0 "$(sed -n '1p' "$PID_FILE")" 2>/dev/null; then
  echo "Sample product is already running with PID $(sed -n '1p' "$PID_FILE")" >&2
  exit 73
fi
rm -f "$PID_FILE"

cleanup() {
  if [ -n "${sample_pid-}" ] && kill -0 "$sample_pid" 2>/dev/null; then
    kill "$sample_pid" 2>/dev/null || true
    wait "$sample_pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
}
trap cleanup EXIT HUP INT TERM

cd "$ROOT"
mvn -q -pl examples/sample-product -am -DskipTests package
mvn -q -pl examples/sample-product dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt

runtime_classpath="$ROOT/examples/sample-product/target/classes:$(sed -n '1p' "$CLASSPATH_FILE")"
java -cp "$runtime_classpath" "$MAIN_CLASS" &
sample_pid=$!
printf '%s\n' "$sample_pid" > "$PID_FILE"

set +e
wait "$sample_pid"
status=$?
set -e
sample_pid=
rm -f "$PID_FILE"

if [ "$status" -ne 0 ]; then
  echo "Sample product flow failed with exit status $status" >&2
  exit "$status"
fi
echo "Sample product exited successfully; leases were released and PID state was cleaned up."
