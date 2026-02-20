FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY Models Models
COPY core core
COPY resources resources
COPY event-service event-service
COPY cron-job cron-job
COPY service service

RUN mvn -pl service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /workspace/service/target/service-0.1.0-SNAPSHOT.jar /app/service.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/service.jar"]
