# ============================================================
# Stage 1 · Build: 在 maven + jdk21 镜像里编译 + 打可执行 jar
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# 拷贝整个 multi-module 仓库（business 依赖 core，必须一起 build）
# 父 pom + core + business
COPY pom.xml ./
COPY ontos-rule-core/pom.xml      ontos-rule-core/pom.xml
COPY ontos-rule-business/pom.xml  ontos-rule-business/pom.xml

# 先拉依赖（这层会被 Docker 缓存，只要 pom 不变就不重拉）
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl ontos-rule-business -am dependency:go-offline || true

# 再拷源码 build
COPY ontos-rule-core/src      ontos-rule-core/src
COPY ontos-rule-business/src  ontos-rule-business/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl ontos-rule-business -am clean package -DskipTests

# 把 fat jar 重命名为固定名字，下一阶段拿
RUN cp ontos-rule-business/target/ontos-rule-business-*.jar /tmp/app.jar

# ============================================================
# Stage 2 · Runtime: 只装 JRE，最小化镜像
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# 装个 curl 用来做 healthcheck
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 非 root 用户跑（更安全）
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
RUN mkdir -p /app/data && chown -R app:app /app

COPY --from=build --chown=app:app /tmp/app.jar /app/app.jar

USER app
EXPOSE 8080

# H2 文件位于 ./data/ontosrule.mv.db（相对 WORKDIR=/app），即 /app/data/ontosrule.mv.db
# 部署时把 host 的 ./data 挂到 /app/data 持久化
VOLUME ["/app/data"]

# JVM 参数：限制堆 + 容器感知
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+UseContainerSupport"

# Spring Boot 启动
# -Dfile.encoding=UTF-8 让中文日志不乱码
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dfile.encoding=UTF-8 -jar /app/app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || \
        curl -fsS http://localhost:8080/api/admin/info || exit 1
