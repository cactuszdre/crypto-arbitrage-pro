# ==========================================
# Dockerfile for Funding Service
# Context expected: backend/services/
# ==========================================

# Use Maven with JDK 17 for build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 1. Copy Parent POM
COPY pom.xml .

# 2. Copy Service POMs (needed for reactor to be happy)
COPY funding-service/pom.xml funding-service/
COPY arbitrage-service/pom.xml arbitrage-service/

# 3. Download dependencies (this layer will be cached if POMs don't change)
# We go offline for the specific project
RUN mvn dependency:go-offline -pl funding-service -am -B

# 4. Copy Source Code
COPY funding-service/src funding-service/src

# 5. Build the application
RUN mvn clean package -DskipTests -pl funding-service -am -B

# ==========================================
# Runtime Stage
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy JAR from build stage
COPY --from=build /app/funding-service/target/*.jar app.jar

# Set permissions
RUN chown -R appuser:appgroup /app

USER appuser

# Expose port 8081
EXPOSE 8081

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
