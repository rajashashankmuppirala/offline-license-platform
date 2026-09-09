#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
KEY_DIR=${1:-"$ROOT/.local/vendor"}
mkdir -p "$KEY_DIR" "$ROOT/samples/licenses/generated"
CLI="$ROOT/license-issuer-cli/target/license-issuer-cli.jar"
if [ ! -f "$CLI" ]; then echo "Build first: mvn -DskipTests package" >&2; exit 2; fi
if [ ! -f "$KEY_DIR/vendor-private.key" ]; then java -jar "$CLI" keygen --private-key "$KEY_DIR/vendor-private.key" --public-key "$KEY_DIR/vendor-public.key"; fi
java -jar "$CLI" issue-license --input "$ROOT/samples/licenses/a-inc-license-input.json" --private-key "$KEY_DIR/vendor-private.key" --public-key "$KEY_DIR/vendor-public.key" --output "$ROOT/samples/licenses/generated/a-inc-license.signed.json"
java -jar "$CLI" verify-license --license "$ROOT/samples/licenses/generated/a-inc-license.signed.json" --public-key "$KEY_DIR/vendor-public.key"
