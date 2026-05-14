#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/interview-assistant}"
SERVICE_SOURCE="${SERVICE_SOURCE:-deploy/systemd/interview-assistant-server.service}"
NGINX_SOURCE="${NGINX_SOURCE:-deploy/nginx/interview-assistant.conf}"
NGINX_TARGET="${NGINX_TARGET:-/etc/nginx/conf.d/interview-assistant.conf}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "请使用 root 或 sudo 执行：sudo bash deploy/scripts/install-configs.sh" >&2
  exit 1
fi

if [[ ! -f "${SERVICE_SOURCE}" || ! -f "${NGINX_SOURCE}" ]]; then
  echo "请在项目根目录执行本脚本，并确认 deploy/systemd 与 deploy/nginx 配置存在" >&2
  exit 1
fi

cp "${SERVICE_SOURCE}" /etc/systemd/system/interview-assistant-server.service
systemctl daemon-reload
systemctl enable interview-assistant-server

cp "${NGINX_SOURCE}" "${NGINX_TARGET}"
nginx -t
systemctl reload nginx || systemctl restart nginx

cat <<EOF
配置安装完成：
- systemd: /etc/systemd/system/interview-assistant-server.service
- nginx: ${NGINX_TARGET}

注意：启动服务前请确认 ${APP_HOME}/server/.env 已配置真实生产变量。
EOF
