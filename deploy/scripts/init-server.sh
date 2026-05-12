#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-interview}"
APP_GROUP="${APP_GROUP:-interview}"
APP_HOME="${APP_HOME:-/opt/interview-assistant}"
LOG_DIR="${LOG_DIR:-/var/log/interview-assistant}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "请使用 root 或 sudo 执行：sudo bash deploy/scripts/init-server.sh" >&2
  exit 1
fi

if ! id "${APP_USER}" >/dev/null 2>&1; then
  useradd --system --create-home --home-dir "${APP_HOME}" --shell /usr/sbin/nologin "${APP_USER}"
fi

mkdir -p \
  "${APP_HOME}/server" \
  "${APP_HOME}/web/dist" \
  "${APP_HOME}/certs/wechat" \
  "${APP_HOME}/certs/alipay" \
  "${LOG_DIR}"

chown -R "${APP_USER}:${APP_GROUP}" "${APP_HOME}" "${LOG_DIR}"
chmod 750 "${APP_HOME}" "${APP_HOME}/server" "${APP_HOME}/certs" "${LOG_DIR}"
chmod 755 "${APP_HOME}/web" "${APP_HOME}/web/dist"

if command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --permanent --add-service=http || true
  firewall-cmd --permanent --add-service=https || true
  firewall-cmd --reload || true
fi

cat <<EOF
服务器目录初始化完成：
- 应用用户：${APP_USER}
- 应用目录：${APP_HOME}
- 日志目录：${LOG_DIR}

下一步：
1. 上传后端 jar 到 ${APP_HOME}/server/
2. 上传并修改 ${APP_HOME}/server/.env
3. 安装 systemd 服务和 Nginx 配置
EOF
