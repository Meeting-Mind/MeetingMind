FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew \
    && ./gradlew --no-daemon bootJar \
    && cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine

RUN apk upgrade --no-cache \
    && addgroup -S meetingmind \
    && adduser -S -G meetingmind -h /app meetingmind
WORKDIR /app
COPY --from=build --chown=meetingmind:meetingmind /workspace/app.jar /app/app.jar

USER meetingmind
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
