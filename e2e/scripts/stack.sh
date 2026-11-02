#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
if [[ -f /data/crew/qzd-5b1e/bin/env.sh ]]; then
  source /data/crew/qzd-5b1e/bin/env.sh
fi
exec 8>.stack.lock
flock 8
case "${1:-}" in
  up)
    command -v heavy >/dev/null || { echo '缺少班子 heavy 闸门，请先 source 工具链 env.sh' >&2; exit 2; }
    heavy sozai-lock heavy -- node scripts/stack.mjs up
    ;;
  down|status) node scripts/stack.mjs "$1" ;;
  *) echo '用法：bash scripts/stack.sh up|down|status' >&2; exit 2 ;;
esac
