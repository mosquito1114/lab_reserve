# ============================================================
# 后端镜像（多阶段构建）
#
# 构建上下文 = 仓库根目录（docker-compose.yml 里 context: .）
#   docker compose build backend
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先只拷 pom，让依赖层能被 Docker 缓存复用
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline

# 再拷源码
COPY backend/src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# curl 用于容器健康检查；tzdata 保证时区正确
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/lab-reserve.jar /app/app.jar

EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
