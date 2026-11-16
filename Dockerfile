# syntax=docker/dockerfile:1

# Base images are pinned by digest (the tag is kept for readability); Dependabot proposes updates.

# --- 1. Build the dashboard / customer PWA --------------------------------
FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS ui
WORKDIR /ui
COPY dashboard-ui/package.json dashboard-ui/package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY dashboard-ui/ ./
RUN npm run build

# --- 2. Build the backend, with the UI served from its static resources ----
FROM eclipse-temurin:21-jdk@sha256:0a3f329821dcadf54b2a3033a15e2e9f007069a9db43d7f51897e6b80e726b07 AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
COPY --from=ui /ui/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /app.jar

# --- 3. Runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre@sha256:18bb4a91a3dea3f03ee31d3755af34dfb40f904fc77ab56cdc2f092e32fff9c2
RUN useradd --system --uid 10001 readybell
USER readybell
COPY --from=build /app.jar /app/readybell.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/readybell.jar"]
