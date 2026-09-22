# ---------- Etapa 0: cockpit (React + Tailwind) ----------
FROM node:20-alpine AS cockpit
WORKDIR /cockpit
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- Etapa 1: build ----------
FROM node:20-alpine AS builder
WORKDIR /app
COPY backend/package*.json ./
RUN npm ci
COPY backend/tsconfig.json backend/nest-cli.json ./
COPY backend/src ./src
RUN npm run build

# ---------- Etapa 2: dependencias de produccion ----------
FROM node:20-alpine AS deps
WORKDIR /app
COPY backend/package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

# ---------- Etapa 3: runtime liviano ----------
FROM node:20-alpine AS runtime
ENV NODE_ENV=production \
    NODE_OPTIONS="--max-old-space-size=192"
WORKDIR /app

RUN addgroup -g 1001 -S driveai && adduser -S driveai -u 1001 -G driveai

COPY --from=deps    --chown=driveai:driveai /app/node_modules ./node_modules
COPY --from=builder --chown=driveai:driveai /app/dist ./dist
COPY --from=cockpit --chown=driveai:driveai /cockpit/dist ./public
COPY --chown=driveai:driveai backend/package.json ./

USER driveai
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8080/api/assistant/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "dist/main.js"]
