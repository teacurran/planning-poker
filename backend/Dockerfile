FROM maven:3.9.9-eclipse-temurin-17 AS build

# Install Node.js for Quinoa frontend build
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs

WORKDIR /workspace

# Copy root pom.xml for dependency resolution
COPY pom.xml ./pom.xml

# Copy frontend for Quinoa plugin
COPY frontend/ ./frontend/

# Copy backend source and resources
COPY backend/src ./backend/src

# Build with Quinoa (bundles frontend into the JAR)
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /opt/app

COPY --from=build /workspace/target/quarkus-app ./quarkus-app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/app/quarkus-app/quarkus-run.jar"]
