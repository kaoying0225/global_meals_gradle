package com.example.global_meals_gradle.controller;

import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.global_meals_gradle.constants.ReplyMessage;
import com.example.global_meals_gradle.dao.CategoryDao;
import com.example.global_meals_gradle.dao.ProductsDao;
import com.example.global_meals_gradle.dao.StyleDao;
import com.example.global_meals_gradle.entity.Category;
import com.example.global_meals_gradle.entity.Products;
import com.example.global_meals_gradle.entity.Staff;
import com.example.global_meals_gradle.entity.Style;
import com.example.global_meals_gradle.req.MonthlyProductsSalesReq;
import com.example.global_meals_gradle.req.ProductCreateReq;
import com.example.global_meals_gradle.req.ProductUpdateReq;
import com.example.global_meals_gradle.res.AdminProductRes;
import com.example.global_meals_gradle.res.MonthlyProductsSalesRes;
import com.example.global_meals_gradle.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
//全局設定此模組的開頭都是 /product
@RequestMapping("/product")
@Tag(name = "商品管理模組", description = "提供商品 CRUD、圖片上傳及銷售報表查詢")
public class ProductsController {

	@Autowired
	private ProductService productService;
	
	@Autowired
    private StyleDao styleDao;
	
    @Autowired
    private CategoryDao categoryDao;
    
    @Autowired
    private ProductsDao productsDao;

	@PostMapping(value = "/create", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	@Operation(summary = "新增商品", description = "上傳商品圖片並新增商品基本資料")
	public AdminProductRes createProduct( //
			@Parameter(content = @Content( //
					mediaType = MediaType.APPLICATION_JSON_VALUE)) //
			@RequestPart("data") @Valid ProductCreateReq req, //
			@Parameter(description = "商品圖片") //
			@RequestPart("file") MultipartFile file, //
			@Parameter(hidden = true) HttpSession session) { //
		return productService.createProduct(req, file, session);
	}

	@PostMapping(value = "/update", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	@Operation(summary = "更新商品", description = "修改商品資訊，可選是否同時更換圖片")
	public AdminProductRes updateProduct( //
			@Parameter(content = //
			@Content( mediaType = MediaType.APPLICATION_JSON_VALUE)) //
			@RequestPart("data") @Valid ProductUpdateReq req, //
			@Parameter(description = "新商品圖片 (選填)") //
			@RequestPart(value = "file", required = false) MultipartFile file, //
			@Parameter(hidden = true) HttpSession session) { //
		return productService.updateProduct(req, file, session);
	}
	
	@GetMapping(value = "/image/{id}")
	@Operation(summary = "獲取商品圖片", description = "根據 ID 讀取圖片二進位流")
	public ResponseEntity<byte[]> getProductImage(@PathVariable("id") int id) {
    Products product = productsDao.findById(id);
    
    if (product == null || product.getFoodImg() == null) {
        return ResponseEntity.notFound().build();
    }

    byte[] imgBytes = product.getFoodImg();
    String mimeType = detectMimeType(imgBytes);

    return ResponseEntity.ok()
            // 關鍵：允許瀏覽器快取這張圖（例如 10 分鐘）
            // 這樣除了第一次，之後刷新網頁圖片會秒出，且不會呼叫後端
            .header(HttpHeaders.CACHE_CONTROL, "max-age=600") 
            .contentType(MediaType.parseMediaType(mimeType))
            .body(imgBytes);
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
	
	@GetMapping("/list")
	@Operation(summary = "取得所有上架商品", description = "查詢目前處於上架狀態的商品列表")
	public AdminProductRes getActiveProducts(@Parameter(hidden = true) HttpSession session) {
		return productService.getActiveProducts(session);
	}

	@GetMapping("/trash")
	@Operation(summary = "取得下架商品", description = "查詢已刪除/下架的商品列表")
	public AdminProductRes getDeletedProducts(@Parameter(hidden = true) HttpSession session) {
		return productService.getDeletedProducts(session);
	}

	@GetMapping("/detail/{id}")
	@Operation(summary = "查詢商品詳情", description = "根據商品 ID 取得單一商品詳細資料")
	public AdminProductRes getProductDetail(@Parameter(description = "商品 ID", example = "1") @PathVariable("id") int id,
			@Parameter(hidden = true) HttpSession session) {
		return productService.getProductById(id, session);
	}

	@PatchMapping("/status/{id}")
	@Operation(summary = "更新商品狀態", description = "部分更新商品的啟用/停用狀態")
	public AdminProductRes updateActiveStatus(@Parameter(description = "商品 ID", example = "1") @PathVariable("id") int id,
			@Parameter(description = "是否啟用", example = "true") @RequestParam("active") boolean active,
			@Parameter(hidden = true) HttpSession session) {
		return productService.updateActiveStatus(id, active, session);
	}
	
	@PostMapping("/delete/{id}")
	@Operation(summary = "刪除商品", description = "執行商品的軟刪除操作")
	public AdminProductRes deleteProduct(
	        @Parameter(description = "要刪除的商品 ID") @PathVariable("id") int id,
	        @Parameter(hidden = true) HttpSession session) {
	    return productService.deleteProduct(id, session);
	}
	
	// 讓前端可以拿清單去渲染下拉選單
    @GetMapping("/styles")
    @Operation(summary = "取得風格", description = "取得全部的商品風格")
    public List<Style> getAllStyles() {
        return styleDao.findAll(); // 這裡可以從 Service 呼叫，或者簡單點直接在 Controller 呼叫 Dao
    }

    @GetMapping("/categories")
    @Operation(summary = "取得餐點分類", description = "取得全部的餐點分類")
    public List<Category> getAllCategories() {
        return categoryDao.findAll();
    }

	// 以下是艷羽寫的
	// Session 的 key 名稱，與 StaffController 保持一致
	// static final：這個類別永遠不變的固定值，放在所有 @Autowired 的上方
	private static final String SESSION_KEY = "loginStaff";

	/*
	 * ================================================================= GET
	 * /api/rm/monthly-sales — 分店長查詢：某年某月該分店所有商品銷售量
	 *
	 * 呼叫範例： GET /api/rm/monthly-sales?year=2026&month=4
	 *
	 * 不需要傳 globalAreaId！分店長的分店 ID 從 Session 取（安全設計）
	 * =================================================================
	 */

	@GetMapping("/rm/monthlysales")
	@Operation(summary = "分店長銷售報表", description = "查詢該分店在指定年月的商品銷售總量 (分店ID取自登入狀態)")
	public MonthlyProductsSalesRes getMonthlySalesByBranch(
			@Parameter(description = "年份", example = "2026") @RequestParam("year") Integer year,
			@Parameter(description = "月份", example = "4") @RequestParam("month") Integer month,
			@Parameter(hidden = true) HttpSession session) {


		// Step 1：從 Session 取出登入的 Staff
		Staff operator = (Staff) session.getAttribute(SESSION_KEY);

		// Step 2：如果 Session 裡沒有 Staff，代表未登入
		if (operator == null) {
			return new MonthlyProductsSalesRes(ReplyMessage.NOT_LOGIN.getCode(), ReplyMessage.NOT_LOGIN.getMessage());
		}

		// Step 3：組裝 Req 物件（只需要 year + month，分店ID從 Session 取）
		MonthlyProductsSalesReq req = new MonthlyProductsSalesReq();
		req.setYear(year);
		req.setMonth(month);

		// Step 4：把 operator 傳給 Service，Service 裡再做「角色是不是 RM」的判斷
		return productService.getMonthlySalesByBranch(req, operator);
	}

	/*
	 * ================================================================= GET
	 * /api/admin/top5-monthly-sales — 老闆查詢：某年某月指定國家前5名
	 *
	 * 呼叫範例： GET /api/admin/top5-monthly-sales?year=2026&month=4&regionId=1
	 *
	 * regionId：前端國家下拉選單選擇後傳入（老闆有權選任何國家，安全的）
	 * =================================================================
	 */

	@GetMapping("/admin/top5monthlysales")
	@Operation(summary = "老闆查詢銷售前五名", description = "查詢指定國家在指定年月的銷售前五名商品")
	public MonthlyProductsSalesRes getTop5MonthlySalesByRegion(
			@Parameter(description = "年份", example = "2026") @RequestParam("year") Integer year,
			@Parameter(description = "月份", example = "4") @RequestParam("month") Integer month,
			@Parameter(description = "區域 ID", example = "1") @RequestParam("regionId") Integer regionId,
			@Parameter(hidden = true) HttpSession session) {


		// Step 1：取 Session 登入者
		Staff operator = (Staff) session.getAttribute(SESSION_KEY);

		// Step 2：未登入檢查
		if (operator == null) {
			return new MonthlyProductsSalesRes(ReplyMessage.NOT_LOGIN.getCode(), ReplyMessage.NOT_LOGIN.getMessage());
		}

		// Step 3：組裝 Req（帶入 regionId，老闆查詢必填）
		MonthlyProductsSalesReq req = new MonthlyProductsSalesReq();
		req.setYear(year);
		req.setMonth(month);
		req.setRegionId(regionId);

		// Step 4：傳給 Service，Service 裡再確認角色是否為 ADMIN
		return productService.getTop5MonthlySalesByRegion(req, operator);
	}
}
