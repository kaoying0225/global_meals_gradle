package com.example.global_meals_gradle.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
// org.springframework 這代表「地基」。說明這個類別是屬於 Spring Framework 這個組織開發的工具，
// 而不是 Java 原生（java.util）或你自己寫的。

// .transaction 這代表「部門」。Spring 有很多功能（網頁、安全、資料庫），這是在告訴系統：
// 我們要找的是跟 「事務（Transaction）」 管理有關的功能區。

//.interceptor 這代表「技術手段」。在 Spring 中，@Transactional 是透過一種叫 「攔截器 (Interceptor)」 
// 的技術來實現的。它像一個保全，攔截你的方法執行，決定什麼時候開始交易，什麼時候結束。

// TransactionAspectSupport 這才是「主角」。
// 它是 Spring 內部用來支援（Support）事務切面（Aspect）的一個基礎類別。
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.global_meals_gradle.constants.ReplyMessage;
import com.example.global_meals_gradle.constants.StaffRole;
import com.example.global_meals_gradle.dao.BranchInventoryDao;
import com.example.global_meals_gradle.dao.CategoryDao;
import com.example.global_meals_gradle.dao.GlobalAreaDao;
import com.example.global_meals_gradle.dao.OrdersDao;
import com.example.global_meals_gradle.dao.ProductsDao;
import com.example.global_meals_gradle.dao.RegionsDao;
import com.example.global_meals_gradle.dao.StyleDao;
import com.example.global_meals_gradle.entity.BranchInventory;
import com.example.global_meals_gradle.entity.Category;
import com.example.global_meals_gradle.entity.GlobalArea;
import com.example.global_meals_gradle.entity.Products;
import com.example.global_meals_gradle.entity.Regions;
import com.example.global_meals_gradle.entity.Staff;
import com.example.global_meals_gradle.entity.Style;
import com.example.global_meals_gradle.req.MonthlyProductsSalesReq;
import com.example.global_meals_gradle.req.ProductCreateReq;
import com.example.global_meals_gradle.req.ProductUpdateReq;
import com.example.global_meals_gradle.res.AdminProductRes;
import com.example.global_meals_gradle.res.MonthlyProductsSalesRes;
import com.example.global_meals_gradle.vo.InventoryDetailVo;
import com.example.global_meals_gradle.vo.MonthlyProductsSalesVo;
import com.example.global_meals_gradle.vo.ProductAdminVo;

import jakarta.servlet.http.HttpSession;

@Service
public class ProductService {

	@Autowired
	private ProductsDao productsDao;

	@Autowired
	private BranchInventoryDao branchInventoryDao;

	@Autowired
	private GlobalAreaDao globalAreaDao;
	
	@Autowired
    private StyleDao styleDao;
	
    @Autowired
    private CategoryDao categoryDao;

	private static final String SESSION_KEY = "loginStaff";

	// 新增商品 ( 同時會新增每個分店庫存為 0 )
	@Transactional(rollbackFor = Exception.class)
	public AdminProductRes createProduct(ProductCreateReq req, MultipartFile file, // 
			HttpSession session) {
	    // 1. 權限檢查
	    AdminProductRes authRes = validateAdminAccess(session);
	    if (authRes != null) {
	    	return authRes;
	    }
	    
	    // 參數檢查
	    AdminProductRes errorsRes = checkParam(req, file, false, null);
	    if (errorsRes != null) { 
	    	return errorsRes;
	    }
	
	    try {
	        Products product = new Products();
	        product.setName(req.getName());
	        product.setDescription(req.getDescription());
	        product.setFoodImg(file.getBytes());
	        product.setActive(req.isActive());
	
	        // --- 重點修正：處理關聯物件 ---
	        // 呼叫你的 getOrCreate 方法，確保資料庫有這筆分類/風格
	        product.setCategory(getOrCreateCategory(req.getCategory()));
	        product.setStyle(getOrCreateStyle(req.getStyle())); 
	        // ---------------------------
	
	        productsDao.save(product);
	
	        // 2. 準備清單存放所有分店的庫存紀錄
	        List<BranchInventory> inventoryList = new ArrayList<>();
	        List<GlobalArea> allBranches = globalAreaDao.getAll();
	
	        if (allBranches.isEmpty()) {
	        	// 手動標記回滾，因為要返回失敗的 Res 物件
	        	TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
	            return new AdminProductRes(ReplyMessage.BRANCH_NOT_FOUND.getCode(), // 
	            		ReplyMessage.BRANCH_NOT_FOUND.getMessage());
	        }
	
			// 3. 迴圈初始化庫存
	        for (GlobalArea area : allBranches) {
	            BranchInventory inventory = new BranchInventory();
	            inventory.setProductId(product.getId());
	            inventory.setGlobalAreaId(area.getId());
	            inventory.setBasePrice(BigDecimal.ZERO);
	            inventory.setCostPrice(BigDecimal.ZERO);
	            inventory.setMaxOrderQuantity(1);
	            inventory.setActive(false);
	            inventory.setUpdatedAt(LocalDateTime.now());
	            inventory.setVersion(1);
	            
	            // 將存好的紀錄加入清單
	            inventoryList.add(inventory);
	        }
	
	        // 使用 saveAll 存入
	        branchInventoryDao.saveAll(inventoryList);
	
	        // 3. 準備回傳資料
	        // 3-1. 獲取分店 Map
	        Map<Integer, String> branchMap = globalAreaDao.getBranchNameMap();
	        
	        // 3-2. 使用 Stream 將 Entity 轉為 VO
	        List<InventoryDetailVo> inventoryVoList = inventoryList.stream()
	                .map(inv -> convertToInventoryDetailVo(inv, branchMap,product.isActive()))
	                .collect(Collectors.toList());
	
	        // 4. 回傳包含完整資訊的 AdminProductRes
	        return new AdminProductRes(ReplyMessage.PRODUCT_CREATE_SUCCESS.getCode(), 
	                ReplyMessage.PRODUCT_CREATE_SUCCESS.getMessage(), 
	                convertToAdminVo(product), 
	                inventoryVoList);
	
	    } catch (IOException e) {
	    	// 圖片讀取失敗，強制回滾
	    	// 【手動觸發事務回滾】
	        // 說明：因為我們使用了 try-catch 攔截異常來回傳自定義的 Res 物件，
	        // 這會導致 Spring 的 @Transactional 偵測不到報錯而誤以為執行成功。
	        // 這裡必須手動存取事務攔截器(TransactionAspectSupport)，將目前的交易狀態(currentTransactionStatus)
	        // 標記為只能回滾(setRollbackOnly)，以確保資料庫操作的原子性（要嘛全成功，要嘛全失敗）。
	    	TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();	     
	        return new AdminProductRes(ReplyMessage.IMAGE_ERROR.getCode(), ReplyMessage.IMAGE_ERROR.getMessage());
	    } catch (Exception e) {
	    	// 其他任何預期外的錯誤，強制回滾
	    	TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();	
	        return new AdminProductRes(ReplyMessage.SYSTEM_ERROR.getCode(), ReplyMessage.SYSTEM_ERROR.getMessage() + e.getMessage());
	    }
	}

	// 修改商品
	@Transactional(rollbackFor = Exception.class)
	public AdminProductRes updateProduct(ProductUpdateReq req, MultipartFile file, HttpSession session) {
	    // 1. 權限檢查
	    AdminProductRes authRes = validateAdminAccess(session);
	    if (authRes != null) return authRes;
	
	    // 先確認商品是否存在
	    Products product = productsDao.findById(req.getId());
	    if (product == null) {
	        return new AdminProductRes(ReplyMessage.PRODUCT_NOT_FOUND.getCode(),
	                ReplyMessage.PRODUCT_NOT_FOUND.getMessage());
	    }
	    
	    if(product.getDeletedAt() != null) {
	    	return new AdminProductRes(ReplyMessage.OPERATE_ERROR.getCode(), 
		            "操作失敗：該商品已刪除");
	    }
	
	    try {
	        // 參數檢查 (現在會檢查 Style 和 Category 是否為空)
	        AdminProductRes errorsRes = checkParam(req, file, true, req.getId());
	        if (errorsRes != null) return errorsRes;
	
	        // --- 更新核心資訊 ---
	        product.setName(req.getName());
	        product.setDescription(req.getDescription());
	        product.setActive(req.isActive());
	
	        // --- 重點修正：處理關聯物件 ---
	        // 使用與新增商品相同的 getOrCreate 邏輯
	        product.setCategory(getOrCreateCategory(req.getCategory()));
	        product.setStyle(getOrCreateStyle(req.getStyle())); 
	        // ---------------------------
	
	        // 如果有傳入新圖片，才更新圖片
	        if (file != null && !file.isEmpty()) {
	            product.setFoodImg(file.getBytes());
	        }
	
	        productsDao.save(product);
	        
	        return new AdminProductRes(ReplyMessage.PRODUCT_UPDATE_SUCCESS.getCode(), 
	                ReplyMessage.PRODUCT_UPDATE_SUCCESS.getMessage(), 
	                convertToAdminVo(product), new ArrayList<>());
	
	    } catch (IOException e) {
	        // 圖片讀取報錯，手動回滾
	        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
	        return new AdminProductRes(ReplyMessage.IMAGE_ERROR.getCode(), ReplyMessage.IMAGE_ERROR.getMessage());
	    } catch (Exception e) {
	        // 【手動觸發事務回滾】
	        // 說明：避免因為 getOrCreate 產生了新分類，但商品更新卻失敗導致的資料不一致。
	        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
	        return new AdminProductRes(ReplyMessage.SYSTEM_ERROR.getCode(), 
	                ReplyMessage.SYSTEM_ERROR.getMessage() + e.getMessage());
	    }
	}

	// 軟刪除商品
	@Transactional(rollbackFor = Exception.class)
	public AdminProductRes deleteProduct(int id, HttpSession session) {
	    // 1. 權限檢查
	    AdminProductRes authRes = validateAdminAccess(session);
	    if (authRes != null) return authRes;
	
	    // 2. 先檢查商品是否存在
	    Products product = productsDao.findById(id);
	    if (product == null) {
	        return new AdminProductRes(ReplyMessage.PRODUCT_NOT_FOUND.getCode(),
	                ReplyMessage.PRODUCT_NOT_FOUND.getMessage());
	    }
	
	    try {
	        // 3. 執行軟刪除
	        int row = productsDao.softDeleteProduct(id);

	        if (row > 0) {
	            // 2. 【新增】連動強制下架所有分店的該商品
	            branchInventoryDao.updateActiveByProductId(id, false);
	            product.setActive(false);
	            return new AdminProductRes(ReplyMessage.PRODUCT_DELETE_SUCCESS.getCode(),
	                    ReplyMessage.PRODUCT_DELETE_SUCCESS.getMessage(),
	                    convertToAdminVo(product), new ArrayList<>());
	        } else {
	            // 如果 row == 0，雖然沒改到資料，但為了安全起見，建議也標記回滾，確保狀態一致
	            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
	            String error = "Delete failed, no rows affected.";
	            return new AdminProductRes(ReplyMessage.SYSTEM_ERROR.getCode(),
	                    ReplyMessage.SYSTEM_ERROR.getMessage() + ": " + error);
	        }
	
	    } catch (Exception e) {
	        // 【手動觸發事務回滾】
	        // 捕捉資料庫層級的所有異常時，必須手動回滾，因為 try-catch 阻斷了 @Transactional 的自動偵測。
	        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
	        
	        return new AdminProductRes(ReplyMessage.SYSTEM_ERROR.getCode(),
	                ReplyMessage.SYSTEM_ERROR.getMessage() + ": " + e.getMessage());
	    }
	}

	// 快速修正上下架
	@Transactional(rollbackFor = Exception.class)
	public AdminProductRes updateActiveStatus(int id, boolean active, HttpSession session) {
		// 管理者權限檢查
		AdminProductRes authRes = validateAdminAccess(session);
		if (authRes != null)
			return authRes;

		Products product = productsDao.findById(id);
		if (product == null) {
			return new AdminProductRes(ReplyMessage.PRODUCT_NOT_FOUND.getCode(), //
					ReplyMessage.PRODUCT_NOT_FOUND.getMessage());
		}
		
		// 新增防護邏輯：如果商品已被軟刪除，禁止執行上架動作
	    if (active && product.getDeletedAt() != null) {
	        return new AdminProductRes(ReplyMessage.OPERATE_ERROR.getCode(), 
	            "操作失敗：該商品已刪除");
	    }
	    
		// 更新狀態
		product.setActive(active);
		productsDao.save(product);

		return new AdminProductRes(ReplyMessage.PRODUCT_UPDATE_SUCCESS.getCode(), //
				ReplyMessage.PRODUCT_UPDATE_SUCCESS.getMessage(), //
				convertToAdminVo(product), new ArrayList<>());
	}

	// 查詢所有「未刪除」的商品 (給清單頁)
	public AdminProductRes getActiveProducts(HttpSession session) {
		AdminProductRes authRes = validateAdminAccess(session);
		if (authRes != null)
			return authRes;

		List<Products> products = productsDao.findByDeletedAtIsNull();
		List<ProductAdminVo> voList = products.stream().map( //
				this::convertToAdminVo).collect(Collectors.toList());

		return new AdminProductRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), voList);
	}

	// 查詢所有「已刪除」的商品 (給垃圾桶頁面)
	public AdminProductRes getDeletedProducts(HttpSession session) {
		AdminProductRes authRes = validateAdminAccess(session);
		if (authRes != null)
			return authRes;

		List<Products> products = productsDao.findByDeletedAtIsNotNull();
		List<ProductAdminVo> voList = new ArrayList<>();

		for (Products p : products) {
			voList.add(convertToAdminVo(p));
		}
		return new AdminProductRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), voList);
	}

	// 查詢單一商品的詳細資料
	public AdminProductRes getProductById(int id, HttpSession session) {
		AdminProductRes authRes = validateProductViewAccess(session);
		if (authRes != null) {
			return authRes;
		}

		Products product = productsDao.findById(id);
		if (product == null) {
			return new AdminProductRes(
					ReplyMessage.PRODUCT_NOT_FOUND.getCode(),
					ReplyMessage.PRODUCT_NOT_FOUND.getMessage());
		}

		return new AdminProductRes(
				ReplyMessage.SUCCESS.getCode(),
				ReplyMessage.SUCCESS.getMessage(),
				convertToAdminVo(product));
	}
	// 工具 - 檢查參數
	private AdminProductRes checkParam(ProductCreateReq req, MultipartFile file, //
			boolean isUpdate, Integer productId) {
		// 1. 名稱檢查
		if (isUpdate) {
			// 修改時：檢查「有沒有別的商品」已經用了這個名字
			if (productsDao.existsByNameAndIdNot(req.getName(), productId) >= 1) {
				return new AdminProductRes(ReplyMessage.PRODUCT_EXISTS.getCode() //
						, ReplyMessage.PRODUCT_EXISTS.getMessage());
			}
		} else {
			// 新增時：檢查名稱是否已存在
			if (productsDao.existsByName(req.getName())) {
				return new AdminProductRes(ReplyMessage.PRODUCT_EXISTS.getCode() //
						, ReplyMessage.PRODUCT_EXISTS.getMessage());
			}
		}

		// 2. 檔案大小檢查 (5MB)
		if (file != null && !file.isEmpty()) {
			if (file.getSize() > 5 * 1024 * 1024) {
				return new AdminProductRes(ReplyMessage.IMAGE_TOO_LARGE.getCode(), //
						ReplyMessage.IMAGE_TOO_LARGE.getMessage());
			}
		}

		// 3. 檢查風格是否存在
		if (req.getStyle() == null || req.getStyle().trim().isEmpty()) {
			return new AdminProductRes(ReplyMessage.STYLE_EMPTY.getCode(), //
					ReplyMessage.STYLE_EMPTY.getMessage());
		}

		// 4. 檢查餐點分類是否存在
		if (req.getCategory() == null || req.getCategory().trim().isEmpty()) {
			return new AdminProductRes(ReplyMessage.CATEGORY_EMPTY.getCode(), //
					ReplyMessage.CATEGORY_EMPTY.getMessage());
		}

		return null;
	}

	// 工具 - 將取出的格式跟 byte 合併傳給前端的 src 直接使用
	private String getFullBase64(byte[] imgBytes) {
	    if (imgBytes == null || imgBytes.length == 0) return "";
	    String mimeType = detectMimeType(imgBytes);
	    String base64 = Base64.getEncoder().encodeToString(imgBytes);
	    return "data:" + mimeType + ";base64," + base64;
	}
	
	// 工具 - 圖片轉換成前端能夠直接使用 -- 這個是原本單純回傳 byte ，後面改成回傳完整的資料
	//	private String encodeImage(byte[] imageBytes) {
	//		return (imageBytes != null && imageBytes.length > 0) ? Base64.getEncoder().encodeToString(imageBytes) : "";
	//	}

	// 取出圖片格式
	private String detectMimeType(byte[] bytes) {
		if (bytes == null || bytes.length < 4)
			return "image/jpeg"; // 預設值

		// 檢查文件頭 (Magic Numbers)
		String hex = String.format("%02X%02X%02X%02X", bytes[0], bytes[1], bytes[2], bytes[3]);

		if (hex.startsWith("89504E47"))
			return "image/png";
		if (hex.startsWith("FFD8FF"))
			return "image/jpeg";
		if (hex.startsWith("47494638"))
			return "image/gif";
		if (hex.startsWith("52494646") && new String(bytes, 8, 4).equals("WEBP"))
			return "image/webp";

		return "image/jpeg"; // 真的都認不出來就猜 jpeg
	}

	// 工具 - 轉換為 Admin VO (給管理者看完整資訊，且前端適用)
	private ProductAdminVo convertToAdminVo(Products p) {
		ProductAdminVo vo = new ProductAdminVo();
		vo.setId(p.getId());
		vo.setName(p.getName());
		
		// --- 重點修正：從物件中提取名稱 ---
	    // 增加 null 檢查比較安全
	    if (p.getCategory() != null) {
	        vo.setCategory(p.getCategory().getName());
	        vo.setCategoryId(p.getCategory().getId());
	    }
	    
	    if (p.getStyle() != null) {
	        vo.setStyle(p.getStyle().getName());
	        vo.setStyleId(p.getStyle().getId());
	    }
	    // ------------------------------
	    
		vo.setDescription(p.getDescription());
		vo.setActive(p.isActive());
		//vo.setFoodImgBase64(getFullBase64(p.getFoodImg()));
		// 取得圖片內容的 HashCode 作為版本號
	    int contentHash = (p.getFoodImg() != null) ? java.util.Arrays.hashCode(p.getFoodImg()) : 0;
	    
	    // 拼接 URL，這會變成像是 /image/6?v=12345678
	    vo.setFoodImgBase64("/lazybaobao/product/image/" + p.getId() + "?v=" + contentHash);
	    return vo;
	}

	// 工具 - 轉換為 InventoryDetailVo VO (給管理者看完整資訊，且前端適用)
	private InventoryDetailVo convertToInventoryDetailVo(BranchInventory inv, Map<Integer, String> branchMap, boolean isMasterActive) {		InventoryDetailVo vo = new InventoryDetailVo();
		vo.setProductId(inv.getProductId());
		vo.setGlobalAreaId(inv.getGlobalAreaId());
		vo.setBasePrice(inv.getBasePrice());
		vo.setCostPrice(inv.getCostPrice());
		vo.setStockQuantity(inv.getStockQuantity());
		vo.setMaxOrderQuantity(inv.getMaxOrderQuantity());
		vo.setActive(inv.isActive());

		// 從傳入的 Map 獲取分店名稱，如果找不到則預設為 "未知分店"
		vo.setBranchName(branchMap.getOrDefault(inv.getGlobalAreaId(), "未知分店"));
		vo.setMasterActive(isMasterActive);
		return vo;
	}
	
	// 工具 - 商品查看權限檢查
	// 用於查詢商品詳情、分店庫存補商品資訊等「只讀」功能
	private AdminProductRes validateProductViewAccess(HttpSession session) {
		Staff staff = (Staff) session.getAttribute(SESSION_KEY);

		// 檢查登入
		if (staff == null) {
			return new AdminProductRes(
					ReplyMessage.NOT_LOGIN.getCode(),
					ReplyMessage.NOT_LOGIN.getMessage());
		}

		// 檢查帳號狀態
		if (!staff.isStatus()) {
			return new AdminProductRes(
					ReplyMessage.ACCOUNT_DISABLED.getCode(),
					ReplyMessage.ACCOUNT_DISABLED.getMessage());
		}

		// 商品詳情屬於查看用途，允許後台管理者、分店長、副店長、員工查詢
		if (staff.getRole() != StaffRole.ADMIN
				&& staff.getRole() != StaffRole.REGION_MANAGER
				&& staff.getRole() != StaffRole.MANAGER_AGENT
				&& staff.getRole() != StaffRole.STAFF) {
			return new AdminProductRes(
					ReplyMessage.OPERATE_ERROR.getCode(),
					"權限不足，無法查看商品資料");
		}

		return null;
	}

	// 2. 工具 - 管理員權限檢查 (用於新增、修改、刪除)
	private AdminProductRes validateAdminAccess(HttpSession session) {
		Staff staff = (Staff) session.getAttribute(SESSION_KEY);

		// 檢查登入
		if (staff == null) {
			return new AdminProductRes(ReplyMessage.NOT_LOGIN.getCode(), ReplyMessage.NOT_LOGIN.getMessage());
		}
		// 檢查狀態
		if (!staff.isStatus()) {
			return new AdminProductRes(ReplyMessage.ACCOUNT_DISABLED.getCode(),
					ReplyMessage.ACCOUNT_DISABLED.getMessage());
		}
		// 檢查權限 (只有 ADMIN 能操作)
		if (staff.getRole() != StaffRole.ADMIN) {
			return new AdminProductRes(ReplyMessage.OPERATE_ERROR.getCode(), "權限不足，僅限管理員操作");
		}
		return null;
	}

	// 內部使用的私有方法，保持代碼乾淨
    private Style getOrCreateStyle(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        return styleDao.findByName(name)
                .orElseGet(() -> {
                    Style s = new Style();
                    s.setName(name);
                    return styleDao.save(s);
                });
    }

    private Category getOrCreateCategory(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        return categoryDao.findByName(name)
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName(name);
                    return categoryDao.save(c);
                });
    }
	
	// 以下為艷羽寫的
	private static final int YEAR_MIN = 2020;
	@Autowired
	private OrdersDao ordersDao;
	@Autowired
	private RegionsDao regionsDao;

	/*
	 * =================================================================
	 * 【功能A】分店長查詢：某年某月 該分店 所有商品銷售量
	 *
	 * 權限規則： - 只有 REGION_MANAGER（分店長）可以呼叫此方法 - 分店長只能看自己分店（globalAreaId）的資料，不能跨店查詢 -
	 * globalAreaId 從 Session 的 operator 物件取，不信任前端傳入
	 *
	 * operator：從 Controller 傳進來的 Session 登入者物件
	 * =================================================================
	 */
	@Transactional(readOnly = true)
	public MonthlyProductsSalesRes getMonthlySalesByBranch(MonthlyProductsSalesReq req, Staff operator) {

		// Step 1：確認是分店長
		// operator.getRole() 取出 Session 裡存的 Staff 的身份（StaffRole enum）
		if (operator.getRole() != StaffRole.REGION_MANAGER) {
			return new MonthlyProductsSalesRes(ReplyMessage.OPERATE_ERROR.getCode(),
					ReplyMessage.OPERATE_ERROR.getMessage());
		}

		// Step 2：年份基本防呆（不能查未來，不能查太久以前）
		int currentYear = java.time.LocalDate.now().getYear();
		if (req.getYear() < YEAR_MIN || req.getYear() > currentYear) {
			return new MonthlyProductsSalesRes(400, "年份超出合法範圍");
		}
		// 未來月份防呆（加在年份防呆的正後面）──
		// 取得當前月份（1 ~ 12）
		int currentMonth = java.time.LocalDate.now().getMonthValue();
		// 條件：今年 且 查的月份 > 現在這個月 = 查未來，擋掉
		if (req.getYear() == currentYear && req.getMonth() > currentMonth) {
			// %d 填年份，%02d 填月份（不足兩位補0，例如4→04）
			return new MonthlyProductsSalesRes(400,
					String.format("%d年%02d月還未結束，暫無銷售數據", req.getYear(), req.getMonth()));
		}

		// Step 3：把 year + month 組成 SQL LIKE 用的字串，例如 "202604%"
		// %d = 年份原樣輸出，%02d = 月份不足兩位補零（4 → "04"），%% = SQL 的萬用字元 %
		String yearMonth = String.format("%d%02d%%", req.getYear(), req.getMonth());

		// Step 4：取出分店長自己的分店 ID（從 Session 取，不信任前端）
		// 這是安全設計的核心：Session 存在伺服器端，前端無法偽造
		int globalAreaId = operator.getGlobalAreaId();

		// Step 5：呼叫 DAO 查詢該分店的銷售資料
		List<Object[]> rawData = ordersDao.getMonthlySalesByBranch(yearMonth, globalAreaId);
		// ── 空結果語意化──
		// 如果查回來是空的，代表這個月/分店確實沒有任何已完成的訂單
		if (rawData == null || rawData.isEmpty()) {
			// 回傳 200（不是錯誤），但附上清楚的說明訊息
			// 傳空清單，讓前端知道是「真的沒有資料」而不是系統錯誤
			return new MonthlyProductsSalesRes(ReplyMessage.SUCCESS.getCode(),
					String.format("%d年%02d月查無銷售記錄", req.getYear(), req.getMonth()), new ArrayList<>() // 明確傳空清單，不是 null
			);
		}

		List<MonthlyProductsSalesVo> salesList = toVoList(rawData);

		return new MonthlyProductsSalesRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(),
				salesList);
	}

	/*
	 * =================================================================
	 * 【功能B】老闆查詢：某年某月 指定國家 所有分店 銷售前5名商品（v2 更新）
	 *
	 * 權限規則： - 只有 ADMIN（老闆）可以呼叫此方法 - 老闆可以選擇任何國家（regionId 由前端傳入是安全的，因為角色已驗過） -
	 * regionId 對應 regions 表的 id 欄位
	 *
	 * operator：從 Controller 傳進來的 Session 登入者物件
	 * =================================================================
	 */
	@Transactional(readOnly = true)
	public MonthlyProductsSalesRes getTop5MonthlySalesByRegion(MonthlyProductsSalesReq req, Staff operator) {

		// Step 1：確認是老闆
		if (operator.getRole() != StaffRole.ADMIN) {
			return new MonthlyProductsSalesRes(ReplyMessage.OPERATE_ERROR.getCode(),
					ReplyMessage.OPERATE_ERROR.getMessage());
		}

		// Step 2：年份防呆
		int currentYear = java.time.LocalDate.now().getYear();
		if (req.getYear() < YEAR_MIN || req.getYear() > currentYear) {
			return new MonthlyProductsSalesRes(400, "年份超出合法範圍");
		}
		// ── 未來月份防呆
		int currentMonth = java.time.LocalDate.now().getMonthValue();
		if (req.getYear() == currentYear && req.getMonth() > currentMonth) {
			return new MonthlyProductsSalesRes(400,
					String.format("%d年%02d月還未結束，暫無銷售數據", req.getYear(), req.getMonth()));
		}

		// Step 3：國家 ID 防呆（老闆查詢必須傳 regionId）
		if (req.getRegionId() == null || req.getRegionId() <= 0) {
			return new MonthlyProductsSalesRes(400, "請選擇國家");
		}
		Regions targetRegion = regionsDao.findById(req.getRegionId()).orElse(null);
		if (targetRegion == null) {
			return new MonthlyProductsSalesRes(400, "國家 ID " + req.getRegionId() + " 不存在，請重新選擇");
		}
		// Step 4：組成 LIKE 字串
		String yearMonth = String.format("%d%02d%%", req.getYear(), req.getMonth());

		// Step 5：查詢指定國家的所有分店 TOP 5
		// SQL 內部會 JOIN global_area + regions 過濾 r.id = regionId
		List<Object[]> rawData = ordersDao.getTop5MonthlySalesByRegion(yearMonth, req.getRegionId());
		// ── 空結果語意化
		// 如果查回來是空的，代表這個月份/國家確實沒有任何已完成的訂單
		if (rawData == null || rawData.isEmpty()) {
			return new MonthlyProductsSalesRes(ReplyMessage.SUCCESS.getCode(),
					String.format("%d年%02d月在此國家查無銷售記錄", req.getYear(), req.getMonth()), new ArrayList<>() // 明確傳空清單，不是
																											// null
			);
		}
		// Step 6：轉 VO（與功能A共用同樣格式）
		// 原本那段 for 迴圈，刪掉，改成：
		List<MonthlyProductsSalesVo> salesList = toVoList(rawData);

		return new MonthlyProductsSalesRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(),
				salesList);
	}

	// =====================================================================
	// 私有工具方法：把 DB 原始資料（Object[]）轉換成前端看得懂的 VO 清單
	// Step 6：把 Object[] 轉成前端看得懂的 VO 物件
	// Object[0] = productName（商品名稱，String）
	// Object[1] = totalQuantity（銷售總量）
	//
	// 【為什麼用 ((Number) row[1]).intValue()？】
	// SUM() 在 MySQL 中永遠回傳 DECIMAL，JPA 會把它映射成 Java 的 BigDecimal。
	// 用 (Number) 父類別接住，再呼叫 intValue()，幫我轉成 int（整數）來用,
	// 比直接 (Integer) 或 (BigDecimal) 更安全，
	// 不管 DB 回的是 BigDecimal / Long / Integer 都不會噴 ClassCastException。
	// 原本那段 for 迴圈，刪掉，改成：
	// =====================================================================
	private List<MonthlyProductsSalesVo> toVoList(List<Object[]> rawData) {
		// 建立一個空清單，準備裝填轉換好的 VO
		List<MonthlyProductsSalesVo> salesList = new ArrayList<>();
		// 逐一把每列原始資料（Object[]）轉成乾淨的 VO 物件
		for (Object[] row : rawData) {
			MonthlyProductsSalesVo vo = new MonthlyProductsSalesVo();
			// row[0] 是 SQL 第一欄 p.name（商品名稱），強制轉成 String
			// row[0] 是 p.name（商品名稱），null 時顯示「未知商品」避免前端爆版
			vo.setProductName(row[0] != null ? (String) row[0] : "未知商品");
			// row[1] 是 SQL 第二欄 SUM(d.quantity)（銷售總量）
			// MySQL SUM() 回傳 DECIMAL，JPA 映射成 BigDecimal
			// 用 Number 父類別接住，再 .intValue() 轉成 int，最安全
			// null 防護：如果 SUM 回傳 null（理論上 GROUP BY 後不該發生，但保險一下），設為 0
			vo.setTotalQuantity(row[1] != null ? ((Number) row[1]).intValue() : 0);
			salesList.add(vo);
		}
		return salesList; // 回傳裝好的清單
	}
}