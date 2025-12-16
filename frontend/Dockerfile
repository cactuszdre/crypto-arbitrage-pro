# Optimized Dockerfile for low-memory builds (1GB RAM)
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies first (cached layer)
COPY package*.json ./
RUN npm ci

# Copy source
COPY . .

# Build with aggressive memory optimization
# Disable parallelism and reduce memory usage
ENV NODE_OPTIONS="--max-old-space-size=460"
ENV NUXT_TELEMETRY_DISABLED=1

# Build Nuxt - single threaded to reduce memory
RUN npm run build || (echo "Build failed, retrying with less memory..." && \
    NODE_OPTIONS="--max-old-space-size=400" npm run build)

# Production stage - minimal image
FROM node:20-alpine AS runner

WORKDIR /app

COPY --from=builder /app/.output ./.output

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3000

EXPOSE 3000

CMD ["node", ".output/server/index.mjs"]
