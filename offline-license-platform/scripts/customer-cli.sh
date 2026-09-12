#!/usr/bin/env sh
set -eu
BASE=${LICENSE_SERVER_URL:-http://127.0.0.1:8443}
: "${LICENSE_SERVER_ADMIN_TOKEN:?Set LICENSE_SERVER_ADMIN_TOKEN}"
command=${1:-help}; shift || true
case "$command" in
  license-status) method=GET; path=/api/v1/license/admin/status;;
  list-instances) method=GET; path=/api/v1/license/admin/instances;;
  list-usage) method=GET; path=/api/v1/license/admin/utilization;;
  audit) method=GET; path=/api/v1/license/admin/audit;;
  generate-activation-request) method=POST; path=/api/v1/license/admin/activation/request; body=${1:?Provide request JSON file};;
  import-license) method=POST; path=/api/v1/license/admin/licenses/import; body=${1:?Provide signed commercial license JSON file};;
  import-activation) method=POST; path=/api/v1/license/admin/activation/import; body=${1:?Provide activation import bundle JSON file};;
  failover|failback) method=POST; path=/api/v1/license/admin/instances/$command; body=${1:?Provide handoff JSON file};;
  backup|validate-restore) method=POST; target=${1:?Provide absolute database path}; escaped=$(printf '%s' "$target" | sed 's/\\/\\\\/g; s/"/\\"/g'); inline_body="{\"path\":\"$escaped\"}"; if [ "$command" = backup ]; then path=/api/v1/license/admin/storage/backup; else path=/api/v1/license/admin/storage/restore/validate; fi;;
  *) echo "Usage: $0 {license-status|generate-activation-request FILE|import-license FILE|import-activation FILE|list-instances|list-usage|audit|failover FILE|failback FILE|backup PATH|validate-restore PATH}" >&2; exit 2;;
esac
if [ "$method" = POST ]; then
  if [ -n "${inline_body:-}" ]; then data=$inline_body; else data=@$body; fi
  curl --fail-with-body --silent --show-error -X POST -H "Authorization: Bearer $LICENSE_SERVER_ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary "$data" "$BASE$path"
else
  curl --fail-with-body --silent --show-error -H "Authorization: Bearer $LICENSE_SERVER_ADMIN_TOKEN" "$BASE$path"
fi
