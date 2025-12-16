# Multi-stage build to reduce memory usage
# Stage 1: Build
FROM node:20-alpine AS builder

# Set memory limit for Node.js
ENV NODE_OPTIONS="--max-old-space-size=450"

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production=false

# Copy source files
COPY . .

# Build the application
RUN npm run build

# Stage 2: Production
FROM node:20-alpine AS runner

WORKDIR /app

# Copy only necessary files from builder
COPY --from=builder /app/.output ./.output
COPY --from=builder /app/package.json ./

# Set environment
ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3000

EXPOSE 3000

# Start the server
CMD ["node", ".output/server/index.mjs"]
