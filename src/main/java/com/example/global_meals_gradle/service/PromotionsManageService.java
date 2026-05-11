package com.example.global_meals_gradle.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.global_meals_gradle.constants.ReplyMessage;
import com.example.global_meals_gradle.dao.ProductsDao;
import com.example.global_meals_gradle.dao.PromotionsDao;
import com.example.global_meals_gradle.dao.PromotionsGiftsDao;
import com.example.global_meals_gradle.entity.Promotions;
import com.example.global_meals_gradle.entity.PromotionsGifts;
import com.example.global_meals_gradle.req.PromotionsManageReq;
import com.example.global_meals_gradle.res.PromotionsListRes;
import com.example.global_meals_gradle.vo.GiftDetailVo;
import com.example.global_meals_gradle.vo.PromotionDetailVo;

@Service
public class PromotionsManageService {

	// 操作 promotions 表：新增活動、刪除活動、開關活動、確認活動是否存在
	@Autowired
	private PromotionsDao promotionsDao;

	// 操作 promotions_gifts 表：新增贈品、關閉所有贈品、依 promotions_id 刪除所有贈品
	@Autowired
	private PromotionsGiftsDao promotionsGiftsDao;

	// 查詢 products 表：確認 gift_product_id 是否有對應的商品存在
	@Autowired
	private ProductsDao productsDao;

	// =============================================
	// 方法零：查詢所有促銷活動（含各自的贈品清單）
	// =============================================

	public PromotionsListRes getList() {

		List<Promotions> allPromotions = promotionsDao.findAll();
		List<PromotionDetailVo> result = new ArrayList<>();

		for (Promotions p : allPromotions) {

			PromotionDetailVo vo = new PromotionDetailVo();
			vo.setId(p.getId());
			vo.setName(p.getName());
			vo.setStartTime(p.getStartTime());
			vo.setEndTime(p.getEndTime());
			vo.setActive(p.isActive());
			vo.setDescription(p.getDescription());

			List<PromotionsGifts> gifts = promotionsGiftsDao.findByPromotionsId(p.getId());
			List<GiftDetailVo> giftVos = new ArrayList<>();
			for (PromotionsGifts g : gifts) {

				GiftDetailVo gvo = new GiftDetailVo();
				gvo.setId(g.getId());
				gvo.setFullAmount(g.getFullAmount());
				gvo.setQuantity(g.getQuantity());
				gvo.setGiftProductId(g.getGiftProductId());

				String name = productsDao.findNameById(g.getGiftProductId());
				gvo.setProductName(name != null ? name : "活動贈品");

				gvo.setActive(g.isActive());
				giftVos.add(gvo);
			}

			vo.setGifts(giftVos);
			result.add(vo);
		}

		return new PromotionsListRes(
				ReplyMessage.SUCCESS.getCode(),
				ReplyMessage.SUCCESS.getMessage(),
				result);
	}

	// =============================================
	// 方法一：新增贈品至活動（寫入 promotions_gifts 表）
	// =============================================

	@Transactional
	public void addPromotionGift(PromotionsManageReq req) {

		if (!promotionsDao.existsById(req.getPromotionsId())) {
			throw new RuntimeException(ReplyMessage.PROMOTION_NOT_FOUND.getMessage());
		}
		if (req.getFullAmount() == null || req.getFullAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException(ReplyMessage.PROMOTION_GIFT_PARAM_ERROR.getMessage());
		}
		if (req.getQuantity() == 0) {
			throw new RuntimeException(ReplyMessage.PROMOTION_GIFT_PARAM_ERROR.getMessage());
		}
		if (req.getGiftProductId() < 1) {
			throw new RuntimeException(ReplyMessage.PROMOTION_GIFT_PARAM_ERROR.getMessage());
		}
		if (productsDao.findNameById(req.getGiftProductId()) == null) {
			throw new RuntimeException(ReplyMessage.PRODUCT_NOT_FOUND.getMessage());
		}

		PromotionsGifts gift = new PromotionsGifts();
		gift.setPromotionsId(req.getPromotionsId());
		gift.setFullAmount(req.getFullAmount());
		gift.setQuantity(req.getQuantity());
		gift.setGiftProductId(req.getGiftProductId());
		gift.setActive(true);
		promotionsGiftsDao.save(gift);
	}

	// =============================================
	// 方法一點五：單獨關閉指定贈品
	// =============================================

	@Transactional
	public void deactivateGift(int giftId) {

		if (!promotionsGiftsDao.existsById(giftId)) {
			throw new RuntimeException(ReplyMessage.PROMOTION_GIFTS_NOT_FOUND.getMessage());
		}

		promotionsGiftsDao.deactivateById(giftId);
	}

	// =============================================
	// 方法二：開關促銷活動
	// =============================================

	@Transactional
	public void togglePromotion(PromotionsManageReq req) {

		Promotions promotion = promotionsDao.findById(req.getPromotionsId())
				.orElseThrow(() -> new RuntimeException(ReplyMessage.PROMOTION_NOT_FOUND.getMessage()));

		promotion.setActive(req.isActive());
		promotionsDao.save(promotion);

		if (!req.isActive()) {
			promotionsGiftsDao.deactivateAllByPromotionsId(req.getPromotionsId());
		}
	}

	// =============================================
	// 方法三：刪除促銷活動（真刪除）
	// =============================================

	@Transactional
	public void deletePromotion(int promotionsId) {

		if (!promotionsDao.existsById(promotionsId)) {
			throw new RuntimeException(ReplyMessage.PROMOTION_NOT_FOUND.getMessage());
		}

		// 先刪 promotions_gifts 再刪 promotions，避免 FK 衝突
		promotionsGiftsDao.deleteByPromotionsId(promotionsId);
		promotionsDao.deleteById(promotionsId);
	}

	// =============================================
	// 方法四：一次建立促銷活動 + 贈品規則
	// =============================================

	@Transactional
	public void createPromotionWithGift(PromotionsManageReq req, byte[] imageBytes) {

		// 圖片為必填
		if (imageBytes == null || imageBytes.length == 0) {
			throw new RuntimeException(ReplyMessage.PROMOTION_IMG_REQUIRED.getMessage());
		}

		if (req.getName() == null || req.getName().isBlank()) {
			throw new RuntimeException(ReplyMessage.PROMOTION_NAME_ERROR.getMessage());
		}
		if (req.getStartTime() == null || req.getEndTime() == null) {
			throw new RuntimeException(ReplyMessage.PROMOTION_DATE_ERROR.getMessage());
		}
		if (req.getStartTime().isBefore(LocalDate.now())) {
			throw new RuntimeException(ReplyMessage.PROMOTION_DATE_ERROR.getMessage());
		}
		if (!req.getEndTime().isAfter(req.getStartTime())) {
			throw new RuntimeException(ReplyMessage.PROMOTION_DATE_ERROR.getMessage());
		}
	    // ✅ 新增：description 長度驗證（對應 DB column length = 500）
        if (req.getDescription() != null && req.getDescription().length() > 500) {
            throw new RuntimeException("活動描述不得超過 500 個字元");
        }

		Promotions promotion = new Promotions();
		promotion.setName(req.getName());
		promotion.setStartTime(req.getStartTime());
		promotion.setEndTime(req.getEndTime());
		promotion.setActive(true);
		promotion.setPromotionImg(imageBytes);
		promotion.setDescription(req.getDescription());

		Promotions saved = promotionsDao.save(promotion);

		if (req.getGiftProductId() > 0) {

			if (req.getFullAmount() == null || req.getFullAmount().compareTo(BigDecimal.ZERO) <= 0) {
				throw new RuntimeException(ReplyMessage.PROMOTION_GIFT_PARAM_ERROR.getMessage());
			}
			if (req.getQuantity() == 0) {
				throw new RuntimeException(ReplyMessage.PROMOTION_GIFT_PARAM_ERROR.getMessage());
			}
			if (productsDao.findNameById(req.getGiftProductId()) == null) {
				throw new RuntimeException(ReplyMessage.PRODUCT_NOT_FOUND.getMessage());
			}

			PromotionsGifts gift = new PromotionsGifts();
			gift.setPromotionsId(saved.getId());
			gift.setFullAmount(req.getFullAmount());
			gift.setQuantity(req.getQuantity());
			gift.setGiftProductId(req.getGiftProductId());
			gift.setActive(true);
			promotionsGiftsDao.save(gift);
		}
	}

	// =============================================
	// 方法五：上傳促銷活動圖片
	// =============================================

	@Transactional
	public void uploadImage(int promotionsId, byte[] imageBytes) {

		Promotions promotion = promotionsDao.findById(promotionsId)
				.orElseThrow(() -> new RuntimeException(ReplyMessage.PROMOTION_NOT_FOUND.getMessage()));

		promotion.setPromotionImg(imageBytes);
		promotionsDao.save(promotion);
	}

	// =============================================
	// 方法六：取得促銷活動圖片
	// =============================================

	public byte[] getImage(int promotionsId) {

		Promotions promotion = promotionsDao.findById(promotionsId)
				.orElseThrow(() -> new RuntimeException(ReplyMessage.PROMOTION_NOT_FOUND.getMessage()));

		return promotion.getPromotionImg();
	}

}
