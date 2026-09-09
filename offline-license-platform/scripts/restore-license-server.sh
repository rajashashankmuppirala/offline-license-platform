#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BACKUP=${1:?Usage: restore-license-server.sh BACKUP}
TARGET=${LICENSE_SERVER_DATA_FILE:-"$ROOT/.local/license-server/data/license-server.db"}
PIDFILE="$ROOT/.local/pids/license-server.pid"
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then echo "Stop the License Server before restore" >&2; exit 2; fi
test "$(sqlite3 "$BACKUP" 'PRAGMA integrity_check;')" = ok
test -f "$TARGET" || { echo "Original database is required for identity validation" >&2; exit 2; }
backup_identity=$(sqlite3 "$BACKUP" "SELECT server_id || ':' || public_key_fingerprint FROM license_server_identity WHERE singleton_id=1;")
target_identity=$(sqlite3 "$TARGET" "SELECT server_id || ':' || public_key_fingerprint FROM license_server_identity WHERE singleton_id=1;")
[ -n "$backup_identity" ] && [ "$backup_identity" = "$target_identity" ] || { echo "Backup does not belong to this installation identity" >&2; exit 2; }
PARTIAL="$TARGET.restore-partial"
PREVIOUS="$TARGET.before-restore"
[ ! -e "$PARTIAL" ] && [ ! -e "$PREVIOUS" ] || { echo "Refusing to overwrite restore staging or previous-state file" >&2; exit 2; }
trap 'rm -f "$PARTIAL"' EXIT HUP INT TERM
cp -p "$BACKUP" "$PARTIAL"
test "$(sqlite3 "$PARTIAL" 'PRAGMA integrity_check;')" = ok
cp -p "$TARGET" "$PREVIOUS"
mv "$PARTIAL" "$TARGET"
trap - EXIT HUP INT TERM
echo "Database restored. Startup will validate identity binding and trusted-time checkpoint."
