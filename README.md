# global_meals_gradle
產業新尖兵 - 後端

懶飽飽 LazyBaoBao - 後端核心系統 (Backend Core)
懶飽飽（LazyBaoBao） 是一個專為台灣在地美食打造的訂餐管理系統。本後端專案負責支撐「客戶端點餐」、「員工 POS 操作」與「老闆管理後台」的所有核心業務邏輯。

我們致力於解決餐飲業最常見的痛點：庫存數據不同步、併發訂單導致的超賣問題，以及自動化的人事流水號管理。透過 Spring Boot 與 JPA 技術，確保每一筆訂單與帳號異動都具備高度的資料一致性。

(此專案為開發練習專題，旨在展現後端架構設計與資料處理能力)

---

使用技術
開發框架：Java 21 + Spring Boot 3.4.x
資料持久化：Spring Data JPA + Hibernate
資料庫：MySQL 8.0
核心機制：Optimistic Locking (樂觀鎖 @Version)、Transactional Consistency

---

🛠️ 關鍵開發亮點

1.雙重並行控制策略 (Dual-Layer Concurrency Control) <br>
為了確保在高流量搶購場景下數據的絕對準確，系統採用了「悲觀」與「樂觀」鎖結合的混合策略： 
- 悲觀鎖 (Pessimistic Locking)： 在生成訂單流水號時，於 DAO 層使用 SELECT ... FOR UPDATE。透過資料庫行鎖機制強制「排隊」，確保每一筆訂單編號（格式：yyyyMMddXXXX）在多執行緒環境下皆唯一且連續，杜絕序號碰撞。
- 樂觀鎖 (Optimistic Locking)： 在扣除商品庫存時，透過 version 欄位進行比對更新。若版本衝突則觸發重試，在保證數據正確性的同時，維持比悲觀鎖更高的系統吞吐量。

2.高可靠重試機制 (Fault-Tolerant Retry Mechanism) <br>
針對因併發衝突導致的寫入失敗，設計了一套健壯的重試流程：
- 指數退避算法 (Exponential Backoff)： 當偵測到序號衝突或版本錯誤時，系統會自動重試（上限 5 次），且每次重試的間隔時間隨次數呈指數增長。
- 隨機抖動 (Jitter)： 在重試間隔中加入隨機毫秒數，有效分散大量請求同時重試對資料庫產生的「驚群效應（Thundering Herd）」。

3.原子性守護： 透過 @Transactional 確保「庫存扣除」、「贈品配額更新」、「訂單生成」與「點數核銷」等動作要麼全數成功，要麼全數回滾，防止出現「超賣」或「收錢沒出貨」的情況。

4.精準計算： 全程使用 BigDecimal 代替浮點數進行財務運算，並嚴格遵循 RoundingMode.HALF_UP 處理稅務與折扣误差，確保對帳精確到每一分錢。

5.動態稅務適配： 系統能根據分店所在區域自動切換內含稅 (Inclusive) 與 外加稅 (Exclusive) 計算模式，並動態校驗各項贈品的促銷門檻。

6.雙角色流程控管： 嚴格區分「員工（櫃檯現場付款/改態）」與「會員（線上預訂/取消）」的操作權限，確保業務流程安全性。 

---

Development Guide
必要環境
JDK：Version 21+
MySQL：Version 8.0+
IDE：IntelliJ IDEA / Eclipse (需安裝 Lombok)

資料庫配置
修改 src/main/resources/application.properties：

spring.datasource.url=jdbc:mysql://localhost:3306/lazybaobao?serverTimezone=Asia/Taipei
spring.datasource.username=你的帳號
spring.datasource.password=你的密碼
spring.jpa.hibernate.ddl-auto=update

指令列表
./gradlew bootRun - 啟動伺服器 (預設埠號 8080)
./gradlew build - 執行專案編譯與打包

---

測試用帳號
已通過權限驗證的現成帳號：

| 職級 | 測試帳號 | 預設密碼 | 權限內容 |

| :--- | :--- | :--- | :--- |

| 老闆 | admin@lazybao.com | admin1234 | 最高權限、全區報表、帳號審核 |

| 分店長 | manager@lazybao.com | mgr1234 | 員工調動、庫存調整、POS 權限 |

| 員工 | staff@lazybao.com | staff1234 | POS 點餐、訂單流轉 |

---

資料夾結構
:open_file_folder: src/main/java/com/example/lazybaobao/ <br>
├── :open_file_folder: config/ # 系統設定 (CORS, JPA 鎖定) <br>
├── :open_file_folder: controller/ # REST API 入口 <br>
├── :open_file_folder: service/ # 業務邏輯 (含帳號生成演算法) <br>
├── :open_file_folder: repository/ # 資料存取 (Spring Data JPA) <br>
├── :open_file_folder: entity/ # 模型 (含 @Version 樂觀鎖) <br>
├── :open_file_folder: dto/ # 資料傳輸物件 <br>
└── :open_file_folder: constant/ # Enum 角色與 ReplyMessage <br>

---

團隊成員: 思云、致遠、艷羽、景翔、昱文、劭頴、家齊

---
最後更新日期：2026-05-30
