FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY libs ./libs
COPY src ./src
RUN gradle build -x test --no-daemon

FROM amazoncorretto:17-alpine3.19
WORKDIR /app
COPY --from=builder /app/build/libs/wsproxy-mock.jar ./app.jar
EXPOSE 8082
CMD ["java", "-jar", "app.jar"]
