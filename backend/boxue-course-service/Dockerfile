# ======================================================
# boxue-course-service 镜像
# 前提：backend 目录已执行 mvn clean package -DskipTests 生成 target/*.jar
# 注意：构建上下文是 backend 根目录，jar 在各模块自己的 target 下，路径须带模块名
# 基础镜像：eclipse-temurin 官方 JDK17 运行版（只含 JRE，镜像小）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY boxue-course-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
