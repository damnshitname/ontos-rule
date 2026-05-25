# ============================================================
# Stage 1 · Build: node 20 跑 vite build
# ============================================================
FROM node:20-alpine AS build

WORKDIR /app

# 拷贝 lockfile 先装依赖（缓存层）
COPY package.json package-lock.json ./
RUN npm ci

# 拷源码 build
COPY . .
RUN npm run build

# ============================================================
# Stage 2 · Runtime: nginx alpine serve dist + reverse proxy
# ============================================================
FROM nginx:1.27-alpine

# 拷自定义 nginx 配置（覆盖默认 default.conf）
COPY --from=build /app/dist /usr/share/nginx/html

# nginx.conf 由 docker-compose 通过 bind mount 注入
# 这里只删掉默认的 server 段，避免和我们的冲突
RUN rm -f /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
