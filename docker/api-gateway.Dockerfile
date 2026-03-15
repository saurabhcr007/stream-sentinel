# Build stage for dependencies
FROM node:20-alpine AS builder

WORKDIR /app
COPY api-gateway/package*.json ./
RUN npm ci --omit=dev

# Final slim production stage
FROM node:20-alpine

WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY api-gateway/package*.json ./
COPY api-gateway/src ./src

# Run as non-root user for security (bonus best practice)
USER node

EXPOSE 3001
CMD ["node", "src/index.js"]
