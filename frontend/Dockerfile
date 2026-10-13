# ============================================================
# 前端镜像（多阶段构建）
#
# 构建上下文 = 仓库根目录（docker-compose.yml 里 context: .）
#   docker compose build frontend
#
# 这样可以复用仓库根的 .dockerignore，也能直接 COPY docker/nginx/default.conf
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM node:22-alpine AS builder
WORKDIR /build

# 先拷依赖清单，利用 Docker 层缓存
COPY frontend/package*.json ./
RUN npm install --no-audit --no-fund

# 再拷源码并构建
COPY frontend/ ./
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf \
 && apk add --no-cache tzdata \
 && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone

COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
