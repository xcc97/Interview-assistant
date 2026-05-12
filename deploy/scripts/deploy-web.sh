#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="${1:-${REMOTE_HOST:-}}"
REMOTE_USER="${REMOTE_USER:-root}"
REMOTE_APP_HOME="${REMOTE_APP_HOME:-/opt/interview-assistant}"
WEB_DIST="${WEB_DIST:-web/dist}"

if [[ -z "${REMOTE_HOST}" ]]; then
  echo "用法：bash deploy/scripts/deploy-web.sh <服务器IP或域名>" >&2
  echo "也可设置环境变量：REMOTE_HOST=your-server bash deploy/scripts/deploy-web.sh" >&2
  exit 1
fi

if [[ ! -f "web/package.json" ]]; then
  echo "请在项目根目录执行本脚本" >&2
  exit 1
fi

npm --prefix web install
npm --prefix web run build

if [[ ! -d "${WEB_DIST}" ]]; then
  echo "未找到前端构建目录：${WEB_DIST}" >&2
  exit 1
fi

TMP_REMOTE_DIR="${REMOTE_APP_HOME}/web/dist.new"

ssh "${REMOTE_USER}@${REMOTE_HOST}" "rm -rf '${TMP_REMOTE_DIR}' && mkdir -p '${TMP_REMOTE_DIR}'"
scp -r "${WEB_DIST}/." "${REMOTE_USER}@${REMOTE_HOST}:${TMP_REMOTE_DIR}/"

ssh "${REMOTE_USER}@${REMOTE_HOST}" "set -e; \
  mkdir -p '${REMOTE_APP_HOME}/web'; \
  if [ -d '${REMOTE_APP_HOME}/web/dist' ]; then rm -rf '${REMOTE_APP_HOME}/web/dist.bak' && mv '${REMOTE_APP_HOME}/web/dist' '${REMOTE_APP_HOME}/web/dist.bak'; fi; \
  mv '${TMP_REMOTE_DIR}' '${REMOTE_APP_HOME}/web/dist'; \
  chown -R interview:interview '${REMOTE_APP_HOME}/web' 2>/dev/null || true; \
  nginx -t; \
  systemctl reload nginx"

cat <<EOF
前端部署完成：
- 服务器：${REMOTE_USER}@${REMOTE_HOST}
- 目录：${REMOTE_APP_HOME}/web/dist
EOF
