package com.example.global_meals_gradle.req;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;

/**
 * 促銷活動管理用的請求參數，涵蓋以下四種用途：
 *   - 新增促銷活動（promotions 表）
 *   - 新增贈品至活動（promotions_gifts 表）
 *   - 開關促銷活動（promotions.is_active）
 *   - 刪除促銷活動（入參只用 promotionsId）
 *
 * 此 Req 被多個端點共用，各端點需要的欄位不同
 * 所有欄位驗證統一在 Service 層手動做，避免 annotation 跨端點互相干擾
 */
public class PromotionsManageReq {

	// =============================================
	// 新增促銷活動（promotions 表）用的欄位
	// =============================================

	// 活動名稱：create 端點必填，不能空白，在 Service 裡驗證
	private String name;

	// 活動開始日期：create 端點必填，不能早於今天，在 Service 裡驗證
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startTime;

	// 活動結束日期：create 端點必填，必須晚於 startTime，在 Service 裡驗證
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endTime;

	// =============================================
	// 新增贈品至活動（promotions_gifts 表）用的欄位
	// =============================================

	// 對應的促銷活動 ID（promotions.id）：
	// addPromotionGift、toggle 端點需要此欄位
	// 在 Service 裡驗證此 ID 是否真的存在於 promotions 表
	private int promotionsId;

	// 消費門檻金額：addPromotionGift 端點必填，必須大於 0，在 Service 裡驗證
	private BigDecimal fullAmount;

	// 贈品配額數量：
	//   -1  → 無限供應（預設值），不會自動扣減
	//   >= 1 → 有限供應，每次結帳扣 1，扣到 0 自動下架
	//   0   → 不合法，在 Service 裡擋掉
	private int quantity = -1;

	// 贈品對應的商品 ID（products.id）：
	// addPromotionGift 端點必填（需 >= 1），在 Service 裡查 products 表確認商品存在
	// create 端點選填（= 0 表示不加贈品，> 0 才建贈品，< 0 不合法）
	private int giftProductId;

	// =============================================
	// 開關促銷活動用的欄位
	// =============================================

	// 活動開關狀態：
	//   true  → 開啟活動（promotions.is_active = 1），只改 promotions，贈品不動
	//   false → 關閉活動（promotions.is_active = 0），同步把底下所有贈品 is_active 設為 0
	private boolean active;
	
	private String description;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalDate startTime) {
		this.startTime = startTime;
	}

	public LocalDate getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalDate endTime) {
		this.endTime = endTime;
	}

	public int getPromotionsId() {
		return promotionsId;
	}

	public void setPromotionsId(int promotionsId) {
		this.promotionsId = promotionsId;
	}

	public BigDecimal getFullAmount() {
		return fullAmount;
	}

	public void setFullAmount(BigDecimal fullAmount) {
		this.fullAmount = fullAmount;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public int getGiftProductId() {
		return giftProductId;
	}

	public void setGiftProductId(int giftProductId) {
		this.giftProductId = giftProductId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
