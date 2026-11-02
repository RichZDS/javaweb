#!/usr/bin/env bash
# 在服务器上运行：把 quiz 库用 mysqldump 导出到 ./backups/quiz-<时间>.sql.gz，只保留最近 7 份。
# 密码不写在脚本里：在数据库容器内用它自己的 MYSQL_ROOT_PASSWORD 环境变量。
# 建议放进 crontab：0 3 * * * ~/quiz-javaweb/backup.sh >> ~/quiz-javaweb/backups/backup.log 2>&1
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
KEEP=7
mkdir -p backups
TS="$(date +%Y%m%d-%H%M%S)"
OUT="backups/quiz-$TS.sql.gz"

docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
    --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 "$MYSQL_DATABASE"' | gzip > "$OUT.tmp"
# 导出失败时 set -o pipefail 会让脚本在这里之前退出，不会留下不完整的备份
mv "$OUT.tmp" "$OUT"
echo "$(date '+%F %T') 备份完成：$OUT（$(du -h "$OUT" | cut -f1)）"

ls -1t backups/quiz-*.sql.gz | tail -n +$((KEEP + 1)) | xargs -r rm --
