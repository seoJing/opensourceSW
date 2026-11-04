FROM node:24.22.2-alpine3.23 AS dependencies
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN npm install --global "$(node -p "require('./package.json').packageManager")" \
    && pnpm install --frozen-lockfile

FROM dependencies AS test
COPY . .
RUN pnpm lint && pnpm typecheck && pnpm test

FROM test AS build
RUN pnpm build

FROM nginx:1.30.6-alpine3.24 AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
