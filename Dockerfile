# ── Build stage ──
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar --no-daemon -x test

# ── Run stage ──
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]