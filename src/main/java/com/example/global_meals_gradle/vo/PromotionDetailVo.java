package com.example.global_meals_gradle.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 單一促銷活動詳細資訊（供 GET /promotions/list 使用）
 *
 * 每一個物件代表 promotions 表的一筆資料，
 * 並附帶該活動底下所有的贈品規則清單（gifts）
 */
public class PromotionDetailVo {

	// promotions 表的 id
	private int id;

	// 活動名稱，對應 promotions.name
	private String name;

	// 活動開始日期，對應 promotions.start_time
	private LocalDate startTime;

	// 活動結束日期，對應 promotions.end_time
	private LocalDate endTime;

	// 活動是否啟用：
	//   true  → 活動進行中（promotions.is_active = 1）
	//   false → 活動已暫停或結束（promotions.is_active = 0）
	private boolean active;

	// 該活動底下所有贈品規則的清單（包含啟用與停用的）
	// 每一個元素是 GiftDetailVo，包含門檻金額、庫存、商品名稱等
	private List<GiftDetailVo> gifts;
	
	private String description;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

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

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public List<GiftDetailVo> getGifts() {
		return gifts;
	}

	public void setGifts(List<GiftDetailVo> gifts) {
		this.gifts = gifts;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
