FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /opt/app

COPY --from=build /workspace/target/quarkus-app ./quarkus-app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/app/quarkus-app/quarkus-run.jar"]
