# nod 点头生产部署清单

本目录提供阿里云服务器上线所需的配置样例和部署脚本：

- `env/server.env.production.example`：后端生产环境变量模板
- `env/web.env.production.example`：前端生产构建环境变量模板
- `systemd/interview-assistant-server.service`：后端 systemd 服务样例
- `nginx/interview-assistant.conf`：前端静态站点 + 后端 API 反向代理样例
- `scripts/init-server.sh`：服务器目录、用户、权限初始化脚本
- `scripts/install-configs.sh`：安装 systemd 与 Nginx 配置脚本
- `scripts/deploy-server.sh`：本地构建并发布后端 jar
- `scripts/deploy-web.sh`：本地构建并发布前端 dist

## 推荐服务器目录

```text
/opt/interview-assistant/
  server/
    interview-assistant-server-1.0.0.jar
    .env
  web/
    dist/
  certs/
    wechat/
    alipay/
  logs/
```

## 首次服务器初始化

把项目或 `deploy/` 目录上传到服务器后，在服务器上执行：

```bash
sudo bash deploy/scripts/init-server.sh
```

该脚本会创建：

- `interview` 系统用户
- `/opt/interview-assistant/server`
- `/opt/interview-assistant/web/dist`
- `/opt/interview-assistant/certs`
- `/var/log/interview-assistant`

然后复制并修改后端生产环境变量：

```bash
sudo cp deploy/env/server.env.production.example /opt/interview-assistant/server/.env
sudo vim /opt/interview-assistant/server/.env
sudo chown interview:interview /opt/interview-assistant/server/.env
sudo chmod 600 /opt/interview-assistant/server/.env
```

至少替换：

- 数据库账号密码
- `INTERVIEW_ASSISTANT_CLIENT_SECRET`
- `INTERVIEW_ASSISTANT_JWT_SECRET`
- 阿里云 NLS / 百炼配置
- `PAYMENT_PUBLIC_BASE_URL`
- 支付宝或微信支付配置

## 安装 systemd 与 Nginx 配置

先修改 `deploy/nginx/interview-assistant.conf` 里的域名和证书路径，然后在服务器上执行：

```bash
sudo bash deploy/scripts/install-configs.sh
```

启动后端：

```bash
sudo systemctl start interview-assistant-server
sudo journalctl -u interview-assistant-server -f
```

## 后端发布

在本地项目根目录执行：

```bash
bash deploy/scripts/deploy-server.sh your-server-ip-or-domain
```

也可以使用环境变量：

```bash
REMOTE_HOST=your-server-ip-or-domain REMOTE_USER=root bash deploy/scripts/deploy-server.sh
```

脚本会自动：

1. 执行 `mvn -f server/pom.xml clean package`
2. 上传 jar 到 `/opt/interview-assistant/server/`
3. 备份旧 jar
4. 重启 `interview-assistant-server`
5. 显示服务状态

## 前端发布

首次发布前，在本地准备前端生产环境变量：

```bash
cp deploy/env/web.env.production.example web/.env.production
# 修改 web/.env.production
```

然后执行：

```bash
bash deploy/scripts/deploy-web.sh your-server-ip-or-domain
```

脚本会自动：

1. 执行 `npm --prefix web install`
2. 执行 `npm --prefix web run build`
3. 上传 `web/dist`
4. 备份旧前端目录
5. 执行 `nginx -t`
6. 重载 Nginx

## 上线前必查

- `PAYMENT_MOCK_ENABLED=false`
- `VITE_ENABLE_MOCK_PAYMENT=false`
- 后端 `8080` 不直接暴露公网，只允许 Nginx 本机代理
- 支付平台回调地址使用 HTTPS 公网域名
- 支付回调可访问：`https://api.your-domain.com/api/payment/alipay/notify`
- 支付成功后订单只入账一次，重复回调不重复发放权益
- MySQL 已开启备份
- `.env` 和证书文件权限已收紧
