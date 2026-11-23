FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 payguard
COPY --from=build /workspace/target/payguard-ai-0.3.0.jar app.jar
USER payguard
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
