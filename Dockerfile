# 容器化編譯(自動模式)
FROM gradle:9-jdk17 AS build

WORKDIR /app

# 利用 Docker 快取機制，先只複製設定檔並下載依賴套件
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon

# 複製剩餘程式碼
COPY . .

# 關鍵：加上 -x test 跳過測試，避免因為連不到資料庫而失敗！
RUN gradle build --no-daemon -x test


# 1. 基礎映像檔改為 17
FROM eclipse-temurin:17-jdk-alpine

# 2. 設定工作目錄
WORKDIR /app

# 3. 將 Gradle 打包好的 JAR 複製到容器中
# 這裡注意：路徑必須指向 build/libs/
COPY --from=build /app/build/libs/*.jar line-bot.jar

# 4. 暴露應用程式埠號 (Spring Boot 預設為 8080)
EXPOSE 8080

# 5. 啟動指令
ENTRYPOINT ["java", "-jar", "line-bot.jar"]