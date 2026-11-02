FROM node:20-alpine

WORKDIR /app
COPY package.json ./
COPY README.md ./
COPY docs ./docs
COPY src ./src

ENV NODE_ENV=production \
    PORT=7000 \
    HOST=0.0.0.0

EXPOSE 7000

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:7000/health >/dev/null || exit 1

USER node
CMD ["node", "src/index.js"]
