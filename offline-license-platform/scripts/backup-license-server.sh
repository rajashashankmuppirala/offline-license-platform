#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TARGET=${1:?Usage: backup-license-server.sh TARGET}
: "${LICENSE_SERVER_ADMIN_TOKEN:?Set LICENSE_SERVER_ADMIN_TOKEN}"
BASE=${LICENSE_SERVER_URL:-http://127.0.0.1:8443}
if [ -e "$TARGET" ]; then echo "Refusing to overwrite $TARGET" >&2; exit 2; fi
escaped=$(printf '%s' "$TARGET" | sed 's/\\/\\\\/g; s/"/\\"/g')
curl --fail-with-body --silent --show-error -X POST \
  -H "Authorization: Bearer $LICENSE_SERVER_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  --data-binary "{\"path\":\"$escaped\"}" "$BASE/api/v1/license/admin/storage/backup" >/dev/null
test -f "$TARGET"
test "$(sqlite3 "$TARGET" 'PRAGMA integrity_check;')" = ok
echo "Backup verified: $TARGET (installation identity intentionally excluded)"
