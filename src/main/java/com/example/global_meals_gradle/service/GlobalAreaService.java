package com.example.global_meals_gradle.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.global_meals_gradle.constants.ReplyMessage;
import com.example.global_meals_gradle.constants.StaffRole;
import com.example.global_meals_gradle.controller.StaffController;
import com.example.global_meals_gradle.dao.GlobalAreaDao;
import com.example.global_meals_gradle.dao.RegionsDao;
import com.example.global_meals_gradle.entity.GlobalArea;
import com.example.global_meals_gradle.entity.Regions;
import com.example.global_meals_gradle.entity.Staff;
import com.example.global_meals_gradle.req.CreateGlobalAreaReq;
import com.example.global_meals_gradle.req.DeleteGlobalAreaReq;
import com.example.global_meals_gradle.req.UpdateGlobalAreaReq;
import com.example.global_meals_gradle.res.BasicRes;
import com.example.global_meals_gradle.res.GlobalAreaRes;
import com.example.global_meals_gradle.res.RegionsRes;
import com.example.global_meals_gradle.utils.PhoneValidatorUtils;

import jakarta.servlet.http.HttpSession;

@Service
public class GlobalAreaService {

	@Autowired
	private GlobalAreaDao globalAreaDao;

	@Autowired
	@Lazy
	private BranchInventoryService branchInventoryService;
	
	@Autowired
	private RegionsDao regionsDao;

	// 新增分店
	@Transactional(rollbackFor = Exception.class)
	public BasicRes create(CreateGlobalAreaReq req, HttpSession session) {
		/* 參數檢查:使用@Valid */
		BasicRes check = checkAdmin(session);
		if (check != null) {
			return check;
		}
		// 根據 regionsId 撈取區域資訊
		Regions regions = regionsDao.getById(req.getRegionsId());
		if (regions == null) {
			return new BasicRes(ReplyMessage.REGIONS_ID_NOT_FOUND.getCode(), //
					ReplyMessage.REGIONS_ID_NOT_FOUND.getMessage());
		}
		try {
			String countryCode = regions.getCountryCode();
			// 電話驗證
			if (!PhoneValidatorUtils.isValid(req.getPhone(), countryCode)) {
				return new BasicRes(ReplyMessage.PHONE_ERROR.getCode(), //
						ReplyMessage.PHONE_ERROR.getMessage());
			}
			// 電話標準化
			String normalizedPhone = PhoneValidatorUtils.toE164Format(req.getPhone(), countryCode);
			globalAreaDao.insert(req.getRegionsId(), req.getBranch(), req.getAddress(), normalizedPhone);
			// 新增分店後，要初始化庫存
			int newBranchId = globalAreaDao.findLastId();
			branchInventoryService.initInventoryForNewBranch(newBranchId);
		} catch (Exception e) {
			throw e;
		}
		return new BasicRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}

	// 更新分店
	@Transactional(rollbackFor = Exception.class)
	public BasicRes update(UpdateGlobalAreaReq req, HttpSession session) {
		BasicRes check = checkAdmin(session);
		if (check != null) {
			return check;
		}
		GlobalArea globalArea = globalAreaDao.findById(req.getId());
		if (globalArea == null) {
			return new BasicRes(ReplyMessage.GLOBAL_AREA_NOT_FOUND.getCode(), //
					ReplyMessage.GLOBAL_AREA_NOT_FOUND.getMessage());
		}
		Regions regions = regionsDao.getById(globalArea.getRegionsId());
		if (regions == null) {
			return new BasicRes(ReplyMessage.REGIONS_ID_NOT_FOUND.getCode(), //
					ReplyMessage.REGIONS_ID_NOT_FOUND.getMessage());
		}
		// 處理 只修改了部分資料，其餘照舊
		String inputBranch = (req.getBranch() == null || req.getBranch().isBlank()) ? globalArea.getBranch() : req.getBranch();
		String inputAddress = (req.getAddress() == null || req.getAddress().isBlank()) ? globalArea.getAddress() : req.getAddress();
		String inputPhone = globalArea.getPhone(); // 預設照舊
		try {
			String countryCode = regions.getCountryCode();
			// 電話有傳入新值才驗證與轉換
			if (req.getPhone() != null && !req.getPhone().isBlank()) {
				// 如果前端有輸入值進來，再來判斷他的最小長度
				if (req.getPhone().length() <= 7) {
					return new BasicRes(ReplyMessage.PHONE_ERROR.getCode(), //
							ReplyMessage.PHONE_ERROR.getMessage());
				}
				if (!PhoneValidatorUtils.isValid(req.getPhone(), countryCode)) {
					return new BasicRes(ReplyMessage.PHONE_ERROR.getCode(), //
							ReplyMessage.PHONE_ERROR.getMessage());
				}
				// 如果值有變動，將舊值更改為新值，並將輸入的值"正規化"存入
				inputPhone = PhoneValidatorUtils.toE164Format(req.getPhone(), countryCode);
			}
			globalAreaDao.update(req.getId(), inputBranch, inputAddress, inputPhone);
		} catch (Exception e) {
			throw e;
		}
		return new BasicRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}

	// 取得分店清單
	public GlobalAreaRes getAllBranch(HttpSession session) {
//		// 權限(所有Staff)
//		Staff staff = (Staff) session.getAttribute(StaffController.SESSION_KEY);
//		if (staff == null) {
//			return new GlobalAreaRes(ReplyMessage.NOT_LOGIN.getCode(), //
//					ReplyMessage.NOT_LOGIN.getMessage());
//		}
		return new GlobalAreaRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), globalAreaDao.getAll());
	}

	// 刪除多個分店
	@Transactional(rollbackFor = Exception.class)
	public BasicRes delete(DeleteGlobalAreaReq req, HttpSession session) {
		BasicRes check = checkAdmin(session);
		if (check != null) {
			return check;
		}
		// 參數格式檢查
		List<Integer> idList = req.getGlobalAreaIdList();
		for (int id : idList) {
			if (id <= 0) {
				return new BasicRes(ReplyMessage.GLOBAL_AREA_ID_ERROR.getCode(), //
						ReplyMessage.GLOBAL_AREA_ID_ERROR.getMessage());
			}
		}
		try {
			// 執行刪除，並取得實際刪除筆數
			int deletedRows = globalAreaDao.delete(idList);
			// 比對筆數：實際刪除筆數 / 請求刪除筆數(req)
			if (deletedRows != idList.size()) {
				// 這裡選擇拋出 RuntimeException 以觸發 @Transactional 的 Rollback
                // 這樣就算前面刪除了 10 筆，只要其中 1 筆不存在，全部都會復原（不刪除）
				throw new RuntimeException(ReplyMessage.DELETED_ENTRIES_ERROR.getMessage());
			}
		} catch (RuntimeException re) {
			// 如果是因為 ID 不存在導致的錯誤，回傳錯誤代碼
			return new BasicRes(ReplyMessage.PARTIAL_DELETED_ID_ERROR.getCode(), //
					ReplyMessage.PARTIAL_DELETED_ID_ERROR.getMessage());
		} catch (Exception e) {
			throw e;
		}
		return new BasicRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
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
