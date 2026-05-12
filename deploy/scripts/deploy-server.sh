#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="${1:-${REMOTE_HOST:-}}"
REMOTE_USER="${REMOTE_USER:-root}"
REMOTE_APP_HOME="${REMOTE_APP_HOME:-/opt/interview-assistant}"
JAR_NAME="${JAR_NAME:-interview-assistant-server-1.0.0.jar}"
LOCAL_JAR="${LOCAL_JAR:-server/target/${JAR_NAME}}"

if [[ -z "${REMOTE_HOST}" ]]; then
  echo "用法：bash deploy/scripts/deploy-server.sh <服务器IP或域名>" >&2
  echo "也可设置环境变量：REMOTE_HOST=your-server bash deploy/scripts/deploy-server.sh" >&2
  exit 1
fi

if [[ ! -f "server/pom.xml" ]]; then
  echo "请在项目根目录执行本脚本" >&2
  exit 1
fi

mvn -f server/pom.xml clean package

if [[ ! -f "${LOCAL_JAR}" ]]; then
  echo "未找到构建产物：${LOCAL_JAR}" >&2
  exit 1
fi

ssh "${REMOTE_USER}@${REMOTE_HOST}" "mkdir -p '${REMOTE_APP_HOME}/server'"
scp "${LOCAL_JAR}" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_APP_HOME}/server/${JAR_NAME}.new"

ssh "${REMOTE_USER}@${REMOTE_HOST}" "set -e; \
  cd '${REMOTE_APP_HOME}/server'; \
  if [ -f '${JAR_NAME}' ]; then cp '${JAR_NAME}' '${JAR_NAME}.bak'; fi; \
  mv '${JAR_NAME}.new' '${JAR_NAME}'; \
  chown interview:interview '${JAR_NAME}' 2>/dev/null || true; \
  systemctl restart interview-assistant-server; \
  systemctl --no-pager --full status interview-assistant-server"

cat <<EOF
后端部署完成：
- 服务器：${REMOTE_USER}@${REMOTE_HOST}
- Jar：${REMOTE_APP_HOME}/server/${JAR_NAME}

查看日志：
ssh ${REMOTE_USER}@${REMOTE_HOST} 'journalctl -u interview-assistant-server -f'
EOF
