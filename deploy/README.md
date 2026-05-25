# Deployment · 部署手册

把 ONTOS Rule Engine 部署到一台 Linux 服务器，对外暴露 **`http://server:19091`**。

```
┌─────────────────────────────────────────────────┐
│  host (Linux)                                   │
│                                                 │
│   :19091  ──────►  frontend (nginx)             │
│                          │                      │
│                          │  /        静态文件   │
│                          │  /api/*  反代        │
│                          ▼                      │
│                    backend (Spring Boot :8080)  │
│                          │                      │
│                          ▼                      │
│                    ./data (H2 file persist)     │
└─────────────────────────────────────────────────┘
```

---

## 0. 前置条件

服务器需要：

| 软件 | 最低版本 | 验证 |
|------|----------|------|
| Docker | 20.10+ | `docker -v` |
| Docker Compose | v2（`docker compose`） | `docker compose version` |
| Git | 任意 | `git --version` |
| 端口 19091 | 空闲 | `ss -tlnp \| grep 19091` 应无输出 |

### Ubuntu / Debian 装 Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER     # 让当前用户用 docker 不用 sudo
newgrp docker                     # 立即生效（或重新登录）
docker -v && docker compose version
```

### CentOS / RHEL 装 Docker

```bash
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker
```

### 防火墙放行 19091

```bash
# ufw（Ubuntu）
sudo ufw allow 19091/tcp

# firewalld（CentOS）
sudo firewall-cmd --permanent --add-port=19091/tcp
sudo firewall-cmd --reload

# iptables（裸）
sudo iptables -I INPUT -p tcp --dport 19091 -j ACCEPT
```

云服务器还要去**安全组**面板把 19091 放出来（阿里云/腾讯云/AWS 都各自管自己的，防火墙开了没用）。

---

## 1. 拉代码

```bash
cd /opt                                                    # 或你喜欢的位置
git clone https://github.com/damnshitname/ontos-rule.git
cd ontos-rule
```

后续只要 `git pull` 即可拉新代码（升级流程见第 4 节）。

---

## 2. 首次启动

```bash
docker compose up -d --build
```

第一次要拉镜像 + 编译，要几分钟（看网速；Maven 拉 CEL 那一波最慢）。

完成后看一眼状态：

```bash
docker compose ps
```

期望输出（两个都是 `Up (healthy)` 才算成功；`starting` 是健康检查还没过，等 1 分钟）：

```
NAME                   STATUS              PORTS
ontos-rule-backend     Up (healthy)        8080/tcp
ontos-rule-frontend    Up (healthy)        0.0.0.0:19091->80/tcp
```

---

## 3. 验证

### 3.1 在服务器上自测

```bash
# 前端首页（200）
curl -sI http://localhost:19091/ | head -1
# 期望：HTTP/1.1 200 OK

# 后端 API（通过 nginx 反代）
curl -s http://localhost:19091/api/rules | head -200
# 期望：JSON 数组（首次可能为空 [] 或 demo 数据）

# 后端聚合统计
curl -s http://localhost:19091/api/invocations/stats
```

### 3.2 从外部访问

浏览器打开 `http://<服务器IP>:19091` —— 应该看到 Vue 后台界面。

---

## 4. 升级 / 重新部署

```bash
cd /opt/ontos-rule
git pull
docker compose up -d --build
```

`up -d --build` 会：
- 检测哪些镜像需要重 build
- 重 build 后只重启发生变化的 service
- 保留 `./data/` 卷不动（H2 数据不丢）

只想强制全量重 build：

```bash
docker compose build --no-cache
docker compose up -d
```

---

## 5. 日常运维

| 操作 | 命令 |
|------|------|
| 看后端日志（实时） | `docker compose logs -f backend` |
| 看 nginx 访问日志 | `docker compose exec frontend tail -f /var/log/nginx/ontos.access.log` |
| 看 nginx 错误日志 | `docker compose exec frontend tail -f /var/log/nginx/ontos.error.log` |
| 重启所有 | `docker compose restart` |
| 单独重启后端 | `docker compose restart backend` |
| 关停（保留数据） | `docker compose down` |
| 关停 + 删除卷 | `docker compose down -v`（⚠️ H2 数据全没！） |
| 进入后端容器 | `docker compose exec backend sh` |
| 看实时资源占用 | `docker stats ontos-rule-backend ontos-rule-frontend` |

---

## 6. 数据备份

H2 文件在 host 的 `./data/` 下：

```bash
# 备份（停服更安全，热备也能跑但可能 inconsistent）
docker compose stop backend
tar czf ontosrule-backup-$(date +%F).tar.gz data/
docker compose start backend

# 还原
docker compose down
tar xzf ontosrule-backup-2026-05-25.tar.gz
docker compose up -d
```

定期做：在 cron 里跑（每天凌晨 3 点）

```cron
0 3 * * *  cd /opt/ontos-rule && tar czf /backup/ontosrule-$(date +\%F).tar.gz data/
```

---

## 7. 性能调优

### JVM 内存

默认 `MaxRAMPercentage=75` —— 容器总内存的 75% 给堆。容器没限内存时按 host 内存算，可能过大。建议在 `docker-compose.yml` 给 backend 加内存限制：

```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          memory: 2G
```

### Nginx 并发

默认配置够中小流量。要扛高并发改 `deploy/nginx.conf` 加：

```nginx
worker_processes auto;
events { worker_connections 4096; }
```

---

## 8. HTTPS（可选）

19091 默认是 HTTP。要 HTTPS 两条路：

### 方案 A · 用 host nginx 反代（推荐）

`docker-compose.yml` 把 frontend 的 ports 改成 `127.0.0.1:19091:80`（只本机访问），然后 host 上跑 nginx + certbot：

```nginx
server {
    listen 443 ssl http2;
    server_name rule.example.com;
    ssl_certificate     /etc/letsencrypt/live/rule.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/rule.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:19091;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

### 方案 B · 在容器里 mount 证书

把 cert 文件 mount 进 frontend 容器的 `/etc/nginx/certs`，改 `deploy/nginx.conf` 加 `listen 443 ssl` 段，compose ports 改 `19091:443`。比较麻烦，证书续签也得自己处理，不推荐。

---

## 9. 切到 PostgreSQL（生产建议）

H2 文件库适合 demo / 小规模。真上生产换 PG。

### 9.1 加 PG service 到 compose

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: ontos-rule-pg
    environment:
      POSTGRES_DB: ontosrule
      POSTGRES_USER: ontos
      POSTGRES_PASSWORD: <强密码>
    volumes:
      - ./pgdata:/var/lib/postgresql/data
    networks:
      - ontos-net

  backend:
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ontosrule
      SPRING_DATASOURCE_USERNAME: ontos
      SPRING_DATASOURCE_PASSWORD: <强密码>
      SPRING_JPA_DATABASE_PLATFORM: org.hibernate.dialect.PostgreSQLDialect
      SPRING_JPA_HIBERNATE_DDL_AUTO: update    # 首次让它建表；之后改 validate + 用 Flyway
```

### 9.2 加 PG 驱动

在 `ontos-rule-business/pom.xml` 加：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

重新 `docker compose up -d --build`。

⚠️ 切库**不会自动迁移**老 H2 数据。如果已有数据要保留，需自己写迁移脚本。

---

## 10. 常见报错

| 现象 | 原因 | 怎么修 |
|------|------|--------|
| `docker compose up` 卡在 maven 拉依赖 | 国内访问 Maven Central 慢 | 改 `ontos-rule-business/Dockerfile` 加阿里云镜像（在 build 阶段加 `-s settings.xml`），或 `--build-arg HTTP_PROXY=...` |
| `Bind for 0.0.0.0:19091 failed: port is already allocated` | 19091 被其它进程占了 | `sudo ss -tlnp \| grep 19091` 找到占用方关掉 |
| 浏览器打开是 nginx 默认页 | nginx.conf 没生效 | `docker compose exec frontend cat /etc/nginx/conf.d/ontos-rule.conf`；确认 host 路径 `./deploy/nginx.conf` 存在 |
| 前端能开但 API 全 502 | backend 没起来 | `docker compose ps`；如果 backend 不是 healthy，看 `docker compose logs backend` |
| 后端起来后立刻退出（exit 1） | 多半是 H2 文件目录没权限 | `sudo chown -R 1000:1000 data/` |
| `docker compose logs backend` 有大段中文乱码 | 终端 locale 不对 | `export LANG=zh_CN.UTF-8`；或忽略，不影响功能 |
| `mvn` build 阶段报内存不足 | host 内存太小（< 2G） | 加 swap：`fallocate -l 2G /swapfile && mkswap /swapfile && swapon /swapfile` |
| Healthcheck 一直 `starting` | start_period 不够（首次 Spring Boot 启动慢） | 等 2 分钟；还不行看 `docker compose logs backend` 找异常 |
| 升级后页面没刷新 | 浏览器缓存 | Ctrl+Shift+R 强刷 |

---

## 11. 卸载

```bash
docker compose down -v       # 停 + 删容器 + 删卷
docker rmi $(docker images 'ontos-rule/*' -q)   # 删镜像
cd .. && rm -rf ontos-rule   # 删代码（数据已经 down -v 没了）
```

---

## 附：完整目录布局

部署完成后服务器 `/opt/ontos-rule/` 下应该是：

```
ontos-rule/
├── data/                            ← H2 数据（自动建）
├── docker-compose.yml
├── deploy/
│   ├── README.md                    ← 本文档
│   └── nginx.conf                   ← nginx 配置
├── ontos-rule-business/
│   ├── Dockerfile
│   └── src/ ...
├── ontos-rule-web/
│   ├── Dockerfile
│   └── src/ ...
└── ...
```
