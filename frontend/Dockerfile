# ======================================================
# boxue-frontend 镜像（多阶段构建）
# 阶段1 builder：用 Node 把 Vue 源码编译成静态文件（dist/）
# 阶段2 runtime：用 Nginx 托管静态文件 + 反向代理 /api 到网关
# 为什么分两阶段：Node 镜像 ~1GB，编译完就没用了；最终镜像只留 Nginx（~50MB）
# 构建上下文：./frontend（见 docker-compose.yml 的 build.context）
# ======================================================

# ---------- 阶段1：构建 ----------
FROM node:22-alpine AS builder
WORKDIR /app

# 关键优化：先只拷 package.json / package-lock.json，再装依赖
# Docker 分层缓存原理：只要这两个文件没变，重跑构建时下面这层 npm ci 直接复用缓存，
# 不会因为你改了 src 里的 .vue 代码就重新下载几百个依赖包
COPY package.json package-lock.json ./

# npm ci = 严格按 package-lock.json 安装（版本完全锁定，可复现）
# npm install 会尝试更新 lock 文件，构建结果不确定，CI/CD 里不用它
# --registry：用淘宝镜像，国内构建快很多（你本机 npm 已配过，但容器里是干净的，必须显式指定）
RUN npm ci --registry=https://registry.npmmirror.com

# 再拷真正的源码（放后面，改代码只会让这一层及以下失效）
COPY . .

# 执行 vite build，产物输出到 /app/dist
RUN npm run build

# ---------- 阶段2：运行 ----------
FROM nginx:1.27-alpine

# 用我们自己的配置覆盖 Nginx 默认配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 从 builder 阶段把编译产物拷过来（--from=builder 是多阶段构建的核心语法）
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
# 官方 nginx 镜像自带 CMD 启动 nginx，这里不写 ENTRYPOINT 也行
