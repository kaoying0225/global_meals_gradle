package com.example.global_meals_gradle.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.global_meals_gradle.constants.ReplyMessage;
import com.example.global_meals_gradle.constants.StaffRole;
import com.example.global_meals_gradle.constants.TaxType;
import com.example.global_meals_gradle.controller.StaffController;
import com.example.global_meals_gradle.dao.RegionsDao;
import com.example.global_meals_gradle.entity.Regions;
import com.example.global_meals_gradle.entity.Staff;
import com.example.global_meals_gradle.req.CreateRegionsReq;
import com.example.global_meals_gradle.req.UpdateRegionsReq;
import com.example.global_meals_gradle.res.BasicRes;
import com.example.global_meals_gradle.res.RegionsRes;

import jakarta.servlet.http.HttpSession;

@Service
public class RegionsService {

	@Autowired
	private RegionsDao regionsDao;
	
	// 新增
	@Transactional(rollbackFor = Exception.class)
	public BasicRes insert(CreateRegionsReq req, HttpSession session) {
		/* 參數檢查:使用@Valid */
		// 登入與權限檢查
		BasicRes check = checkAdmin(session);
		if (check != null) {
			return check;
		}
		// 檢查傳進來的是否為 INCLUSIVE / EXCLUSIVE
		if (!TaxType.check(req.getTaxType())) {
			return new BasicRes(ReplyMessage.TAX_TYPE_ERROR.getCode(), //
					ReplyMessage.TAX_TYPE_ERROR.getMessage());
		}
		// 新增國家稅值
		try {
			regionsDao.insert(req.getCountry(), req.getCurrencyCode().toUpperCase(), //
					req.getCountryCode().toUpperCase(), req.getTaxRate(), //
					req.getTaxType().toUpperCase());
		} catch (Exception e) {
			throw e;
		}
		return new BasicRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}
	
	// 修改
	@Transactional(rollbackFor = Exception.class)
	public BasicRes update(UpdateRegionsReq req, HttpSession session) {
		
		/* 更新要檢查 regions 表的 id(PK)，因為存在 DB 中的 id 一定是 大於0 */
		// 直接在 UpdateRegionsReq 做資料驗證即可
//		if(req.getId() <= 0) {
//			return new BasicRes(ReplyMessage.REGIONS_ID_ERROR.getCode(), //
//					ReplyMessage.REGIONS_ID_ERROR.getMessage());
//		}
		// 登入與權限檢查
		BasicRes check = checkAdmin(session);
		if (check != null) {
			return check;
		}
		// 先確認是否有這筆資料
		Regions regions = regionsDao.getById(req.getId());
		if (regions == null) {
			return new BasicRes(ReplyMessage.REGIONS_ID_NOT_FOUND.getCode(), //
					ReplyMessage.REGIONS_ID_NOT_FOUND.getMessage());
		}
		// 處理 只修改了部分資料，其餘照舊
		BigDecimal inputTaxRate;
		if (req.getTaxRate() == null) {
			inputTaxRate = regions.getTaxRate();
		} else {
			// 同時檢查負數與超過 100% 的稅率
			if (req.getTaxRate().compareTo(BigDecimal.ZERO) < 0
					|| req.getTaxRate().compareTo(BigDecimal.ONE) > 0) {
				return new BasicRes(ReplyMessage.TAX_RATE_ERROR.getCode(), //
						ReplyMessage.TAX_RATE_ERROR.getMessage());
			}
			inputTaxRate = req.getTaxRate();
		}
		String inputTaxType;
		if (!StringUtils.hasText(req.getTaxType())) {
			inputTaxType = regions.getTaxType().name();
		} else {
			if (!TaxType.check(req.getTaxType())) {
				return new BasicRes(ReplyMessage.TAX_TYPE_ERROR.getCode(), //
						ReplyMessage.TAX_TYPE_ERROR.getMessage());
			}
			inputTaxType = req.getTaxType().toUpperCase();
		}
		try {
			regionsDao.update(req.getId(), inputTaxRate, inputTaxType);
		} catch (Exception e) {
			throw e;
		}
		return new BasicRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}
	
	// 取得各國基本設定
	public RegionsRes getAll(HttpSession session) {
//		// 權限(所有Staff)
//		Staff staff = (Staff) session.getAttribute(StaffController.SESSION_KEY);
//		if (staff == null) {
//			return new RegionsRes(ReplyMessage.NOT_LOGIN.getCode(), //
//					ReplyMessage.NOT_LOGIN.getMessage());
//		}
		return new RegionsRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), regionsDao.getAll());
	}
	
	/* 統一權限檢查(僅ADMIN)
	 * return null 表示通過；非 null 表示有錯誤 */
	private BasicRes checkAdmin(HttpSession session) {
		// 驗證是否登入
		Staff staff = (Staff) session.getAttribute(StaffController.SESSION_KEY);
		if (staff == null) {
			return new BasicRes(ReplyMessage.NOT_LOGIN.getCode(), //
					ReplyMessage.NOT_LOGIN.getMessage());
		}
		// 權限驗證(ADMIN)
		if (staff.getRole() != StaffRole.ADMIN) {
			return new BasicRes(ReplyMessage.ROLE_ERROR.getCode(), //
					ReplyMessage.ROLE_ERROR.getMessage());
		}
		return null;
	}
	
}
