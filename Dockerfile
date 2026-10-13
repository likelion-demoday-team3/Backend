# 백엔드 이미지. deploy/docker-compose.yml이 이 파일로 빌드한다.

# ===== 1단계: jar 빌드 =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 먼저 받아서 캐시 (소스만 바뀌면 이 단계는 다시 하지 않는다)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# 테스트는 CI·로컬에서 돌리고, 이미지 빌드에서는 건너뛴다
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v plain)" app.jar

# ===== 2단계: 실행 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 spring
COPY --from=build /workspace/app.jar app.jar
USER spring

EXPOSE 8080
# 컨테이너 메모리의 75%까지 힙으로 쓴다 (EC2 메모리에 맞춰 자동 조절)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
