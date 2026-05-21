# Multi-stage build for the notes service.
#
#   Stage 1: maven:3.9-eclipse-temurin-21 — runs `mvn package` so the build
#            environment doesn't pollute the runtime image.
#
#   Stage 2: eclipse-temurin:21-jre — JRE-only image with just the produced
#            jar. Smaller footprint, fewer CVEs.
#
# server.port in application.yml is 3213 and server.servlet.context-path is
# /notes, so the service responds at http://<host>:3213/notes/api/notes/*
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

ARG GIT_COMMIT=unknown
ARG BUILD_NUMBER=unknown
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.revision="${GIT_COMMIT}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.source="https://bitbucket.org/<workspace>/secure-vault-notes"
LABEL bitbucket.build.number="${BUILD_NUMBER}"
LABEL version="${BUILD_NUMBER}"

COPY --from=build /app/target/*.jar app.jar
EXPOSE 3213
ENTRYPOINT ["java", "-jar", "app.jar"]
