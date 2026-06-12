package com.example.global_meals_gradle.utils;

import java.util.Map;
import java.util.TreeMap;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class EcpayUtils {

	// 綠界加密邏輯：SHA-256 產生綠界要求的檢查碼 (CheckMacValue)
    public static String generateCheckMacValue(String hashKey, String hashIV, Map<String, String> params) {
        // 1. 使用 TreeMap 自動將所有傳入參數依據字母 A-Z 進行排序 (綠界強制要求)
        TreeMap<String, String> sortedParams = new TreeMap<>(params);

        // 2. 組合字串(參數最前面要加 HAshKey，最後面要加 HashIV)：HashKey=xxx&key1=value1&key2=value2&...&HashIV=xxx
        StringBuilder sb = new StringBuilder();
        // 依照規則，最前面要放 HashKey
        sb.append("HashKey=").append(hashKey);
        // 迴圈取出排序好的參數，拼成 key1=value1&key2=value2 的格式
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            sb.append("&").append(entry.getKey()).append("=").append(entry.getValue());
        }
        // 最後面加上 HashIV
        sb.append("&HashIV=").append(hashIV);

        // 3. 執行符合綠界規範的 URL Encode（特殊符號需手動替換）
        String encoded = ecpayUrlEncode(sb.toString());

        // 4. 轉小寫
        encoded = encoded.toLowerCase();

        // 5. SHA-256 加密並轉大寫，產出最終檢查碼
        return sha256(encoded).toUpperCase();
    }

    // 綠界特有的 URL Encode 規則（非常坑，一定要照這個換）
    private static String ecpayUrlEncode(String str) {
        try {
        	// 先做標準 UTF-8 編碼，再將 Java 預設的編碼符號替換成綠界指定的格式
            return URLEncoder.encode(str, StandardCharsets.UTF_8.toString())
                    .replace("%2d", "-").replace("%5f", "_").replace("%2e", ".")
                    .replace("%2a", "*").replace("%28", "(").replace("%29", ")")
                    .replace("%20", "+");
        } catch (Exception e) {
        	 throw new RuntimeException("綠界 URL Encode 失敗", e);
        }
    }

    // 標準 SHA-256 數位雜湊演算法
    private static String sha256(String base) {
        try {
        	// 呼叫 Java 內建的安全加密套件
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 將字串轉為 Byte 陣列後進行雜湊運算
            // 把剛才算好的 SHA-256 原始數據拿出來
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            // 用來拼接字串的工具
            StringBuilder hexString = new StringBuilder();
            // 將 Byte 結果轉換為 16 進制的字串格式
            for (byte b : hash) {
            	// 【關鍵】0xff & b 是為了處理 Java 的補數問題，確保轉出來的數字是正數
                // Integer.toHexString 會把數字轉成 16 進制（例如 10 變成 a，15 變成 f）
                String hex = Integer.toHexString(0xff & b);
                // 如果轉出來只有一位數（例如 5），為了格式整齊，要在前面補個 '0' 變成 '05'
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA256 加密失敗", ex);
        }
    }
}
