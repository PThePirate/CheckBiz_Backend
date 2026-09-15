# --- Etapa 1: build ---
FROM gradle:8.9-jdk21 AS build
WORKDIR /app

# Cachear dependencias antes de copiar el código (acelera rebuilds)
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon || true

COPY src ./src
RUN gradle bootJar --no-daemon -x test

# --- Etapa 2: runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S checkbiz && adduser -S checkbiz -G checkbiz
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /app/uploads && chown -R checkbiz:checkbiz /app
USER checkbiz

EXPOSE 4000
ENTRYPOINT ["java", "-jar", "app.jar"]
