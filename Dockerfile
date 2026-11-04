# syntax=docker/dockerfile:1

# --- 1. Build the dashboard / customer PWA --------------------------------
FROM node:20-alpine AS ui
WORKDIR /ui
COPY dashboard-ui/package.json dashboard-ui/package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY dashboard-ui/ ./
RUN npm run build

# --- 2. Build the backend, with the UI served from its static resources ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
COPY --from=ui /ui/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /app.jar

# --- 3. Runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 readybell
USER readybell
COPY --from=build /app.jar /app/readybell.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/readybell.jar"]
