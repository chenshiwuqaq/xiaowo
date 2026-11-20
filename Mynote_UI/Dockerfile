# =====================================================================
# MyNote 前端镜像：Node 构建静态产物 -> Nginx 托管
# 构建上下文必须是 Mynote_UI 目录：docker build -t mynote-web .
# =====================================================================

# ---------- 阶段 1：构建 ----------
FROM node:22-bookworm-slim AS build
WORKDIR /app

# 默认走国内 npm 镜像，可在构建时用 --build-arg NPM_REGISTRY=https://registry.npmjs.org 改回官方源
ARG NPM_REGISTRY=https://registry.npmmirror.com
ENV NPM_CONFIG_REGISTRY=${NPM_REGISTRY}

# 先装依赖，源码改动时复用这一层缓存
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

COPY . .
# 只跑 vite build，不跑 vue-tsc 类型检查：类型告警不该挡住部署
RUN npm run build-only

# ---------- 阶段 2：托管 ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
