#!/usr/bin/env bash
# 在本机运行：打后端 jar、构建 H5（和管理后台，如果仓库里已有 admin/），把部署文件传到服务器，
# 远程 docker compose up -d，最后检查健康接口。服务器上的 .env、backups/、releases/ 不会被覆盖或删除。
#
# 用法：deploy/deploy.sh [--dry-run] [--skip-build] [--env-file <文件>]
#   --dry-run     只打印将要执行的命令，不构建、不传输、不连服务器
#   --skip-build  不重新构建，直接用上次的产物
#   --env-file    连接信息文件，默认 deploy/.env.deploy（模板 deploy/env.deploy.example）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/deploy/.env.deploy"
DRY_RUN=0
SKIP_BUILD=0

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --env-file) ENV_FILE="$2"; shift ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "未知参数：$1（--help 查看用法）" >&2; exit 2 ;;
  esac
  shift
done

if [ ! -f "$ENV_FILE" ]; then
  echo "找不到连接信息文件 $ENV_FILE；复制 deploy/env.deploy.example 为 deploy/.env.deploy 后填写" >&2
  exit 1
fi
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a
: "${DEPLOY_HOST:?$ENV_FILE 里缺 DEPLOY_HOST}"
DEPLOY_SSH_PORT="${DEPLOY_SSH_PORT:-22}"
# 相对路径就是服务器上用户主目录下的路径（不要写 ~，否则会在本机展开）
DEPLOY_DIR="${DEPLOY_DIR:-quiz-javaweb}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:18090/quiz/api/app/version?platform=android}"
APK_PATH="${APK_PATH:-}"
# 可选：放在 ssh 前面的命令，服务器只能密码登录时用，例如 "sshpass -f /path/to/pwfile"（密码放在文件里，不写在这里）
DEPLOY_SSH_PREFIX="${DEPLOY_SSH_PREFIX:-}"
# 服务器上调用 docker 的方式；ubuntu 用户不在 docker 组时设成 "sudo docker"
DEPLOY_DOCKER="${DEPLOY_DOCKER:-docker}"

# 有前缀（密码登录）时不能用 BatchMode=yes，否则 ssh 不会接受 sshpass 提供的密码
SSH_OPTS=(-p "$DEPLOY_SSH_PORT")
if [ -z "$DEPLOY_SSH_PREFIX" ]; then
  SSH_OPTS+=(-o BatchMode=yes)
fi
# docker 要 sudo 时分配终端，sudo 需要密码就能在这里输入（配置了免密 sudo 则不会提示）
case "$DEPLOY_DOCKER" in
  sudo\ *) SSH_OPTS+=(-t) ;;
esac
read -r -a SSH_PREFIX <<< "$DEPLOY_SSH_PREFIX"
SSH=("${SSH_PREFIX[@]}" ssh "${SSH_OPTS[@]}" "$DEPLOY_HOST")
# rsync 的 -e 只要“怎么连”的命令，不带 -t
if [ -n "$DEPLOY_SSH_PREFIX" ]; then
  RSYNC_SSH="$DEPLOY_SSH_PREFIX ssh -p $DEPLOY_SSH_PORT"
else
  RSYNC_SSH="ssh -p $DEPLOY_SSH_PORT -o BatchMode=yes"
fi
DC="$DEPLOY_DOCKER compose"
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/quiz-deploy.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT

# 打印并执行；--dry-run 时只打印
run() {
  printf '+ %s\n' "$*"
  if [ "$DRY_RUN" -eq 0 ]; then "$@"; fi
}
# 在某个目录里执行构建命令；本机有班子的重任务闸门就走闸门（heavy 外层、sozai-lock 内层）
build_in() {
  local dir=$1; shift
  local cmd=("$@")
  if command -v heavy >/dev/null 2>&1 && command -v sozai-lock >/dev/null 2>&1; then
    cmd=(heavy sozai-lock heavy -- "$@")
  fi
  printf '+ (cd %s) %s\n' "${dir#"$ROOT"/}" "${cmd[*]}"
  if [ "$DRY_RUN" -eq 0 ]; then (cd "$dir" && "${cmd[@]}"); fi
}
step() { printf '\n== %s\n' "$*"; }

step "1/6 构建"
if [ "$SKIP_BUILD" -eq 1 ]; then
  echo "（--skip-build：沿用上次的产物）"
else
  build_in "$ROOT/backend" mvn -B -ntp clean package
  build_in "$ROOT/app" npm ci
  build_in "$ROOT/app" npm run build:h5
  if [ -f "$ROOT/admin/package.json" ]; then
    build_in "$ROOT/admin" npm ci
    build_in "$ROOT/admin" npm run build
  else
    echo "（仓库里还没有 admin/，跳过管理后台构建）"
  fi
fi

step "2/6 整理部署目录 $STAGE"
JAR="$ROOT/backend/target/quiz-backend.jar"
H5="$ROOT/app/dist/build/h5"
ADMIN="$ROOT/admin/dist"
if [ "$DRY_RUN" -eq 0 ]; then
  [ -f "$JAR" ] || { echo "找不到 $JAR" >&2; exit 1; }
  [ -f "$H5/index.html" ] || { echo "找不到 H5 构建产物 $H5/index.html" >&2; exit 1; }
fi
run mkdir -p "$STAGE/app" "$STAGE/nginx" "$STAGE/www/download"
run cp "$ROOT/deploy/docker-compose.yml" "$ROOT/deploy/backup.sh" "$ROOT/deploy/.env.example" "$STAGE/"
run cp "$ROOT/deploy/nginx.conf" "$STAGE/nginx/default.conf"
run cp "$JAR" "$STAGE/app/quiz-backend.jar"
run cp -R "$H5/." "$STAGE/www/"
if [ -f "$ADMIN/index.html" ]; then
  run mkdir -p "$STAGE/www/admin"
  run cp -R "$ADMIN/." "$STAGE/www/admin/"
else
  echo "（没有管理后台产物 $ADMIN，这次不更新 /quiz/admin/）"
fi
run cp "$ROOT/deploy/download/index.html" "$STAGE/www/download/index.html"
if [ -n "$APK_PATH" ]; then
  run cp "$APK_PATH" "$STAGE/www/download/quiz.apk"
fi

step "3/6 服务器上留一份当前版本（releases/，保留最近 3 份）"
run "${SSH[@]}" "mkdir -p $DEPLOY_DIR/releases $DEPLOY_DIR/backups && cd $DEPLOY_DIR && \
  if [ -f app/quiz-backend.jar ]; then tar czf releases/\$(date +%Y%m%d-%H%M%S).tgz docker-compose.yml nginx app www; fi && \
  ls -1t releases/*.tgz 2>/dev/null | tail -n +4 | xargs -r rm --"

step "4/6 同步文件（不碰服务器上的 .env、backups/、releases/）"
run rsync -az --delete -e "$RSYNC_SSH" \
  --exclude '.env' --exclude 'backups/' --exclude 'releases/' \
  "$STAGE/" "$DEPLOY_HOST:$DEPLOY_DIR/"

step "5/6 启动"
# 先等数据库健康，再把 backend 和 web 各重建一次（换上新 jar 和新配置）。
# 只重建一次，不在 up 之后再 restart：首次启动时 Flyway 正在建表，中途重启会留下半截的库。
run "${SSH[@]}" "cd $DEPLOY_DIR && test -f .env || { echo '服务器上缺少 ~/$DEPLOY_DIR/.env，先照 .env.example 填写' >&2; exit 1; }; \
  chmod +x backup.sh && $DC up -d --wait db && \
  $DC up -d --force-recreate --remove-orphans backend web"

step "6/6 健康检查 $HEALTH_URL"
run "${SSH[@]}" "for i in \$(seq 1 30); do if curl -fsS '$HEALTH_URL'; then echo; exit 0; fi; sleep 5; done; \
  echo '健康检查失败，看日志：$DC logs --tail 200 backend' >&2; exit 1"

if [ "$DRY_RUN" -eq 1 ]; then
  printf '\n（--dry-run：以上命令都没有执行）\n'
else
  printf '\n部署完成。\n'
fi
