# Production-only Dockerfile - assumes .output is pre-built
# Use this with GitHub Actions or local build
FROM node:20-alpine

WORKDIR /app

# Copy pre-built output (built by GitHub Actions or locally)
COPY .output ./.output

# Set environment
ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3000

EXPOSE 3000

# Start the server - no build needed!
CMD ["node", ".output/server/index.mjs"]
