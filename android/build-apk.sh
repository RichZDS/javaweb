#!/usr/bin/env bash
# 一条命令出签名的正式安装包：android/dist/quiz-<versionName>.apk
#
#   android/build-apk.sh
#
# 版本号、网址在 android/gradle.properties；签名证书和密码只在本机的 keystore.env 里，
# 这个脚本把它们读进环境变量交给 Gradle，不打印、不写进任何文件。
#   QUIZ_KEY_ENV   签名配置文件，默认 /data/workspace/quiz-release/keystore.env
#                  内容：KEYSTORE=证书路径  ALIAS=别名  STORE_PASSWORD=…  KEY_PASSWORD=…
#   ANDROID_HOME   Android SDK，默认 /data/tools/android-sdk
#   GRADLE_USER_HOME  Gradle 缓存，默认 /data/tools/gradle-home
set -euo pipefail
cd "$(dirname "$0")"

export ANDROID_HOME="${ANDROID_HOME:-/data/tools/android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/data/tools/gradle-home}"
KEY_ENV="${QUIZ_KEY_ENV:-/data/workspace/quiz-release/keystore.env}"

[ -d "$ANDROID_HOME/platforms" ] || { echo "找不到 Android SDK：$ANDROID_HOME" >&2; exit 1; }
[ -r "$KEY_ENV" ] || { echo "读不到签名配置：$KEY_ENV" >&2; exit 1; }

# 在函数里读签名配置，变量不外泄，只导出 Gradle 要的四个
load_signing() {
  local KEYSTORE='' ALIAS='' STORE_PASSWORD='' KEY_PASSWORD=''
  # shellcheck disable=SC1090
  . "$KEY_ENV"
  [ -n "$KEYSTORE" ] && [ -n "$ALIAS" ] && [ -n "$STORE_PASSWORD" ] || { echo "$KEY_ENV 里缺 KEYSTORE / ALIAS / STORE_PASSWORD" >&2; exit 1; }
  case "$KEYSTORE" in /*) ;; *) KEYSTORE="$(cd "$(dirname "$KEY_ENV")" && pwd)/$KEYSTORE" ;; esac
  [ -r "$KEYSTORE" ] || { echo "读不到证书文件：$KEYSTORE" >&2; exit 1; }
  export QUIZ_KEYSTORE="$KEYSTORE" QUIZ_KEY_ALIAS="$ALIAS" QUIZ_STORE_PASSWORD="$STORE_PASSWORD"
  export QUIZ_KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"
}
load_signing

VERSION=$(sed -n 's/^quizVersionName=//p' gradle.properties)
OUT="dist/quiz-$VERSION.apk"

echo "== 单元测试 + 打正式包（versionName $VERSION）"
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:assembleRelease

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "$OUT"

echo "== 校验签名"
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
"$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" | grep -E 'Signer #1 certificate (DN|SHA-1|SHA-256) digest|Signer #1 certificate DN'
echo "== 产物"
ls -l "$OUT" | awk '{printf "%s  %.1f MB\n", $NF, $5/1048576}'
