# syntax=docker/dockerfile:1.7
FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d AS base
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends openssl ca-certificates

FROM base AS dependencies
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/package.json
COPY services/api/package.json services/api/package.json
COPY packages/openapi-client/package.json packages/openapi-client/package.json
RUN npm ci --ignore-scripts

FROM dependencies AS build
COPY tsconfig*.json ./
COPY eslint.config.mjs ./
COPY apps/web ./apps/web
COPY services/api ./services/api
RUN npm run prisma:generate --workspace=@lunara/api \
    && npm run build --workspace=@lunara/api \
    && npm run build --workspace=@lunara/web

FROM dependencies AS production-dependencies
COPY services/api/prisma ./services/api/prisma
RUN npm ci --omit=dev \
    && npm run prisma:generate --workspace=@lunara/api

FROM debian:bookworm-slim@sha256:abd67ffcfa541b485a3dff59865ab629aa048a6c613e639d36e7456b0b229241 AS runtime
ENV NODE_ENV=production \
    PORT=3000
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends openssl ca-certificates libstdc++6 \
    && groupadd --gid 10001 lunara \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin lunara
WORKDIR /app
COPY --from=base /usr/local/bin/node /usr/local/bin/node
COPY --from=production-dependencies --chown=lunara:lunara /workspace/node_modules ./node_modules
# npm workspaces may place non-hoisted production packages below the workspace.
# Preserve that runtime dependency tree as well (for example `jose`).
COPY --from=production-dependencies --chown=lunara:lunara /workspace/services/api/node_modules ./services/api/node_modules
COPY --from=build --chown=lunara:lunara /workspace/services/api/dist ./services/api/dist
COPY --from=build --chown=lunara:lunara /workspace/services/api/package.json ./services/api/package.json
COPY --from=build --chown=lunara:lunara /workspace/services/api/prisma ./services/api/prisma
COPY --from=build --chown=lunara:lunara /workspace/apps/web/dist ./apps/web/dist
USER 10001:10001
EXPOSE 3000
CMD ["node", "services/api/dist/main.js"]
