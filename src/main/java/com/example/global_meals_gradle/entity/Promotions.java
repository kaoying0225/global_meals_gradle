package com.example.global_meals_gradle.entity;

import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "promotions")
public class Promotions {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private int id;

	@Column(name = "name")
	private String name;

	@Column(name = "start_time")
	private LocalDate startTime;

	@Column(name = "end_time")
	private LocalDate endTime;

	@Column(name = "is_active")
	private boolean active;

	@Lob
	@Basic(fetch = FetchType.LAZY)
	@Column(name = "promotion_img")
	private byte[] promotionImg;

	@Column(name = "description", length = 500)
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

	public byte[] getPromotionImg() {
		return promotionImg;
	}

	public void setPromotionImg(byte[] promotionImg) {
		this.promotionImg = promotionImg;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
