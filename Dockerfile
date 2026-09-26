# syntax=docker/dockerfile:1

# ---------- Giai đoạn 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Tải dependency trước để lớp này được cache khi chỉ sửa mã nguồn
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Test đã chạy ở máy dev/CI (mvn verify); build image chỉ đóng gói
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && cp target/aloute-*.jar /build/app.jar

# ---------- Giai đoạn 2: chạy ----------
FROM eclipse-temurin:21-jre-alpine
# Không chạy bằng root. uid/gid 1000 trùng user thường đầu tiên trên VPS (deploy), để đọc được file khóa Firebase
# (chmod 600, chủ là deploy) được gắn từ ./secrets. /app/uploads dành cho ảnh khi dùng ALOUTE_STORAGE=local.
RUN addgroup -S -g 1000 aloute && adduser -S -u 1000 -G aloute aloute \
 && mkdir -p /app/uploads && chown -R aloute:aloute /app
WORKDIR /app
COPY --from=build --chown=aloute:aloute /build/app.jar app.jar
USER aloute

# Heap = 60% RAM của container; thoát hẳn khi hết bộ nhớ để Docker tự khởi động lại thay vì treo
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/login || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
