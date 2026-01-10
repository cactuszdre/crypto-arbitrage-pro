# Build stage
FROM maven:3.9.6-eclipse-temurin-17-focal AS build
WORKDIR /app

# Copy parent pom first
COPY pom.xml .

# Copy service poms
COPY funding-service/pom.xml funding-service/
COPY arbitrage-service/pom.xml arbitrage-service/
COPY auth-service/pom.xml auth-service/

# Download dependencies (go-offline)
# We need to copy src to allow reactor detection if needed, or just install parent
# This is a bit tricky with multi-module. Standard way:
COPY . .
RUN mvn clean package -pl auth-service -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-focal
WORKDIR /app
COPY --from=build /app/auth-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
