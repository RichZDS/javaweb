#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
if [[ -f /data/crew/qzd-5b1e/bin/env.sh ]]; then
  source /data/crew/qzd-5b1e/bin/env.sh
fi
exec 8>.stack.lock
flock 8
case "${1:-}" in
  up|down|status) node scripts/stack.mjs "$1" ;;
  *) echo '用法：bash scripts/stack.sh up|down|status' >&2; exit 2 ;;
esac
