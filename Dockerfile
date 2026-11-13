# 1. Java 21 환경에서 Spring Boot 빌드
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar --no-daemon

# 2. 빌드된 애플리케이션 실행
FROM eclipse-temurin:21-jre

WORKDIR /app

# root가 아닌 전용 사용자로 실행
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=builder /app/build/libs/*.jar app.jar

USER app

EXPOSE 8080

# 컨테이너 메모리(ECS task 1GB)의 75%까지만 힙으로 사용해 메모리 초과 종료를 막는다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
