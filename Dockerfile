# syntax=docker/dockerfile:1

# Base images are pinned by digest (the tag is kept for readability); Dependabot proposes updates.

# --- 1. Build the dashboard / customer PWA --------------------------------
FROM node:20-alpine@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS ui
WORKDIR /ui
COPY dashboard-ui/package.json dashboard-ui/package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY dashboard-ui/ ./
RUN npm run build

# --- 2. Build the backend, with the UI served from its static resources ----
FROM eclipse-temurin:25-jdk@sha256:b8705e936964c228dfce359e63dbf08f6c69cb380ed1d576b1f6b1064f15567d AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
COPY --from=ui /ui/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /app.jar

# --- 3. Runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre@sha256:a640968ae55027b322cedd5f8228b01a84baff0b9e9360e47601c6a111ba1699
RUN useradd --system --uid 10001 readybell
USER readybell
COPY --from=build /app.jar /app/readybell.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/readybell.jar"]
