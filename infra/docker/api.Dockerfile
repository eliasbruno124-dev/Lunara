# syntax=docker/dockerfile:1.7
FROM node:24.21.0-bookworm-slim@sha256:d6aa754f16b3197301076f047b5def2f02ea1dbbc2ca920407d46d7ec7f87b20 AS base
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
ENV NODE_OPTIONS=--max-old-space-size=768 \
    npm_config_jobs=2
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

FROM debian:bookworm-slim@sha256:7c7b2c966bc9ee8cedfeef67e0e279108992c77681fa595db4a9d65c06ccc587 AS runtime
LABEL org.opencontainers.image.version="1.3.4-node-24.21.0"
ENV NODE_ENV=production \
    PORT=3000
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends perl-base openssl ca-certificates libstdc++6 \
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
COPY --chown=lunara:lunara artifacts/lunara-release.apk ./apps/web/dist/downloads/lunara.apk
USER 10001:10001
ARG VCS_REF=unknown
LABEL org.opencontainers.image.revision="${VCS_REF}"
EXPOSE 3000
CMD ["node", "services/api/dist/main.js"]
