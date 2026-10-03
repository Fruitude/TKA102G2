# ========================================================
# 階段一：Maven 編譯打包 (Multi-Stage Build)
# ========================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 先複製 pom.xml 並利用快取
COPY pom.xml .

# 複製原始碼 (包含 Java 程式碼、resources 及 webapp/JSP)
COPY src ./src

# 執行 Maven 打包 (跳過測試以加速建置)
RUN mvn clean package -DskipTests

# ========================================================
# 階段二：輕量化 JRE 執行環境
# ========================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# 設定時區為台北時間
ENV TZ=Asia/Taipei
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 從階段一複製編譯完成的 WAR 檔
COPY --from=builder /app/target/TKA102G2.war app.war

# 暴露 Spring Boot 預設 Port
EXPOSE 8080

# 啟動應用程式
ENTRYPOINT ["java", "-jar", "app.war"]
