FROM node:22-alpine
WORKDIR /app
COPY mcp/package.json ./
COPY mcp/src ./src
ENV PORT=8090
EXPOSE 8090
CMD ["node", "src/index.js"]
