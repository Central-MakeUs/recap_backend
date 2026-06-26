# ── Build stage ──
FROM amazoncorretto:17-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew && \
    java -version && \
    ./gradlew dependencies --no-daemon --stacktrace

COPY src src

RUN ./gradlew clean build --no-daemon -x test

# ── Run stage ──
FROM amazoncorretto:17-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "app.jar", "--spring.profiles.active=prod"]