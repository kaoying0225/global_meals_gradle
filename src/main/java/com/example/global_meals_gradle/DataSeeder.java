//package com.example.global_meals_gradle;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Random;
//import java.util.concurrent.ThreadLocalRandom;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import com.example.global_meals_gradle.constants.OperationType;
//import com.example.global_meals_gradle.constants.ReplyMessage;
//import com.example.global_meals_gradle.controller.MembersController;
//import com.example.global_meals_gradle.controller.StaffController;
//import com.example.global_meals_gradle.dao.BranchInventoryDao;
//import com.example.global_meals_gradle.dao.DiscountDao;
//import com.example.global_meals_gradle.dao.MembersDao;
//import com.example.global_meals_gradle.dao.OrderCartDao;
//import com.example.global_meals_gradle.dao.OrderCartDetailsDao;
//import com.example.global_meals_gradle.dao.OrdersDao;
//import com.example.global_meals_gradle.dao.ProductsDao;
//import com.example.global_meals_gradle.dao.PromotionsGiftsDao;
//import com.example.global_meals_gradle.dao.RegionsDao;
//import com.example.global_meals_gradle.entity.BranchInventory;
//import com.example.global_meals_gradle.entity.Discount;
//import com.example.global_meals_gradle.entity.Members;
//import com.example.global_meals_gradle.entity.OrderCart;
//import com.example.global_meals_gradle.entity.OrderCartDetails;
//import com.example.global_meals_gradle.entity.Orders;
//import com.example.global_meals_gradle.entity.Products;
//import com.example.global_meals_gradle.entity.Regions;
//import com.example.global_meals_gradle.entity.Staff;
//import com.example.global_meals_gradle.req.CreateOrdersReq;
//import com.example.global_meals_gradle.res.CreateOrdersRes;
//import com.example.global_meals_gradle.res.MembersRes;
//import com.github.javafaker.Faker;
//
//import jakarta.servlet.http.HttpSession;
//
//import org.springframework.mock.web.MockHttpSession; // 如果報錯，代表沒載入 spring-test
//
//@Component
//public class DataSeeder implements CommandLineRunner {
//
//	private static final org.slf4j.Logger log = org.slf4j //
//			.LoggerFactory.getLogger(DataSeeder.class);
//
//	@Autowired
//	private OrderCartDao orderCartDao;
//
//	@Autowired
//	private OrderCartDetailsDao orderCartDetailsDao;
//
//	@Autowired
//	private OrdersDao ordersDao;
//
//	@Autowired
//	private ProductsDao productsDao;
//
//	@Autowired
//	private BranchInventoryDao branchInventoryDao;
//
//	@Autowired
//	@Lazy // 加上 @Lazy 避免某些 Spring 版本出現循環依賴的警告
//	private DataSeeder self;
//
//	@Autowired
//	private MembersDao membersDao;
//
//	@Autowired
//	private DiscountDao discountDao;
//
//	@Autowired
//	private PromotionsGiftsDao promotionsGiftsDao;
//
//	@Autowired
//	private RegionsDao regionsDao;
//	
//	private final Faker faker = new Faker(new Locale("zh-TW"));
//
//	@Override
//	public void run(String... args) {
//		boolean enableSeeding = false; // 💡 手動改為 true 才會跑
//	    if (!enableSeeding) return;
//		
//		Random random = new Random();
//
//		// 1. 取得所有庫存資料，這樣保證 product_id 與 global_area_id 都是真實且匹配的
//		List<BranchInventory> allInventory = branchInventoryDao.findAllByActive();
//
//		if (allInventory.isEmpty()) {
//			System.out.println("❌ 資料庫沒商品庫存，請先在 branch_inventory 表加入資料！");
//			return;
//		}
//
//		int testData = 0; // 控制要產生幾筆測試資料
//		System.out.println("🚀 開始生成" + testData + "筆關聯假資料...");
//
//		for (int i = 0; i <= testData; i++) {
//			// 從現有庫存中「隨機挑選一個品項」，這會自動帶出該品項所屬的分店 ID
//			BranchInventory inventoryItem = allInventory.get(random.nextInt(allInventory.size()));
//			Integer currentBranchId = inventoryItem.getGlobalAreaId(); // 確保這個 ID 是真實存在的
//			System.out.println("當前抓到的分店 ID: " + currentBranchId);
//
//			// 2. 建立購物車 (對應你的 image_ddaa58.png)
//			OrderCart orderCart = new OrderCart();
//			orderCart.setGlobalAreaId(currentBranchId);
//			orderCart.setOperation(1);
//			orderCart.setOperationType(OperationType.CUSTOMER);
//			orderCartDao.save(orderCart);
//
//			int quantity = faker.number().numberBetween(1, 5); // 隨機數量 1~5
//
//			// 計算小計：單價 * 數量
//
//			// 3. 建立購物車細項 (對應你的 image_dda777.png)
//			OrderCartDetails orderCartDetails = new OrderCartDetails();
//			orderCartDetails.setOrderCartId(orderCart.getId()); // 關聯剛剛建立的購物車
//			orderCartDetails.setProductId(inventoryItem.getProductId());
//			orderCartDetails.setQuantity(quantity);
//			orderCartDetails.setPrice(inventoryItem.getBasePrice());
//			orderCartDetails.setGift((boolean) false);
//			orderCartDetailsDao.save(orderCartDetails);
//
//			// 3. 封裝成 Request 物件 (模擬前端傳來的資料)
//			CreateOrdersReq req = new CreateOrdersReq();
//			req.setOrderCartId(orderCart.getId());
//			req.setGlobalAreaId(currentBranchId);
//			req.setMemberId(orderCart.getOperation()); // 預設會員
//			req.setPhone(faker.phoneNumber().cellPhone());
//			req.setOrderCartDetailsList(List.of(orderCartDetails)); // 把細項塞進去供計算
//			req.setUseDiscount(false);
//			req.setPromotionsId(0);
//			
//			// 💡 新增：隨機產生 2026 年之間的日期
//		    // int month = faker.number().numberBetween(3, 6); // 幾月到幾月
//		    int day = faker.number().numberBetween(1, 10); // 幾日到幾日
//		    LocalDate randomDate = LocalDate.of(2026, 5, day);
//		    String randomDateStr = randomDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
//
//			try {
//				System.out.println("🚀 呼叫 Service 執行正式下單流程...");
//				// 注意：你可能需要 mock 一個 HttpSession 或修改 Service
//				// 讓它在 Session 為空時也能執行 (或傳入 Dummy Session)
//				createOrders(req, new MockHttpSession(), randomDateStr);
//				System.out.println("✅ 第 " + (i + 1) + " 筆產單成功！");
//			} catch (Exception e) {
//				System.err.println("❌ 下單失敗：" + e.getMessage());
//			}
//		}
//
//		System.out.println("✅" + testData + "筆假資料生成完畢，金額已自動換算！");
//	}
//
//	/* 成立訂單: 外部呼叫的主入口：負責「高併發重試流程」 */
//	// 這個方法「不加」@Transactional，這樣裡面的 try-catch 才能重複執行。
//	public CreateOrdersRes createOrders(CreateOrdersReq req, HttpSession httpSession, String randomDateStr) {
//		// 抓員工資訊
//		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
//		// 抓會員資訊(因為會員登入那邊存的是res，所以會多一層)
//		MembersRes membersRes = (MembersRes) httpSession.getAttribute(MembersController.ATTRIBUTE_KEY);
//		Members member = (membersRes != null) ? membersRes.getMembers() : null;
//		if (staff != null) { // 代表是員工操作
//			if (req.getMemberId() <= 0) {
//				return new CreateOrdersRes(ReplyMessage.MEMBER_NOT_FOUND.getCode(),
//						ReplyMessage.MEMBER_NOT_FOUND.getMessage());
//			}
//			if (req.getMemberId() > 1 && membersDao.findById(req.getMemberId()) == null) {
//				return new CreateOrdersRes(ReplyMessage.MEMBER_NOT_FOUND.getCode(),
//						ReplyMessage.MEMBER_NOT_FOUND.getMessage());
//			}
//		} else if (member != null) { // 代表會員操作
//			req.setMemberId(member.getId());
//		} else { // 代表是遊客
//			if (req.getMemberId() > 1) { // 可能是會員，但session失效(登出) "連線已逾時，請重新登入後再結帳"
//				return new CreateOrdersRes(ReplyMessage.NOT_LOGIN.getCode(), //
//						ReplyMessage.NOT_LOGIN.getMessage());
//			}
//			req.setMemberId(1);
//		}
//
//		// [DEBUG] 記錄請求進入，方便追蹤
//		log.debug("【訂單請求】收到購物車 ID: {}, 會員 ID: {}", req.getOrderCartId(), req.getMemberId());
//
//		if (req.isUseDiscount()) {
//			if (req.getMemberId() == 1) {
//				// [WARN] 記錄異常的折扣請求（可能是前端繞過或邏輯錯誤）
//				log.warn("【訂單攔截】購物車id {} 嘗試使用折扣但資格不符", req.getOrderCartId());
//				throw new RuntimeException("無優惠可使用");
//			}
//			Members memberForDiscount = membersDao.findById(req.getMemberId());
//			if (memberForDiscount == null || !memberForDiscount.isDiscount()) {
//				// [WARN] 記錄異常的折扣請求（可能是前端繞過或邏輯錯誤）
//				log.warn("【訂單攔截】會員 {} 嘗試使用折扣但資格不符", req.getMemberId());
//				throw new RuntimeException("無優惠可使用");
//			}
//		}
//		if (ordersDao.existsByOrderCartId(req.getOrderCartId())) {
//			throw new RuntimeException("該購物車已轉換為訂單，請勿重複提交");
//		}
//
//		// 取得今天的日期字串，例如 "20260328"
//		// DateTimeFormatter.ofPattern("yyyyMMdd"): 定義日期格式 .format(...):
//		// 把得到的日期轉換成前面定義的格式
//		// String todayStr =
//		// LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
//		String todayStr = randomDateStr;
//
//		// 設定最大重試次數。如果很多人同時搶號碼，失敗了就重新跑一次迴圈。
//		int maxRetries = 5;
//
//		for (int i = 0; i < maxRetries; i++) {
//			try {
//				// 必須透過 self. 呼叫，否則事務 (@Transactional) 會失效！
//				return self.executeInsert(req, todayStr);
//			} catch (RuntimeException e) {
//				// 判斷是否為「可重試」的異常
//				String msg = e.getMessage();
//				// 購物車Id在資料庫有設UQ，所以如果連續點擊，會有錯誤訊息，到這裡就會傳送錯誤訊息給前端
//				if (msg != null && msg.contains("order_cart_id")) {
//					throw new RuntimeException("該購物車已轉換為訂單，請勿重複提交");
//				}
//				// 只有當訊息包含「衝突」(樂觀鎖失敗) 或 「Duplicate」(序號重複) 時才進入重試
//				if (msg != null && (msg.contains("衝突") || msg.contains("Duplicate")//
//						|| msg.contains("Primary"))) {
//					log.info("【訂單重試】購物車 ID: {} 發生衝突，準備進行第 {} 次重試... 原因: {}", //
//							req.getOrderCartId(), i + 1, msg);
//					// 如果還沒超過重試次數，就繼續跑下一輪 for 迴圈。
//					if (i == maxRetries - 1) {
//						// 如果重試了 5 次都還是失敗，才拋出錯誤。
//						// [ERROR] 記錄重試耗盡，這代表系統併發極高，可能需要優化
//						log.error("【訂單失敗】購物車 ID: {} 重試 {} 次後仍失敗", //
//								req.getOrderCartId(), maxRetries);
//						throw new RuntimeException("系統繁忙，請重新結帳");
//					}
//
//					// 指數退避: 越多次，時間越久
//					long sleepTime = 50 * (long) Math.pow(2, i);
//
//					// 加隨機 jitter（0~50ms）
//					sleepTime += ThreadLocalRandom.current().nextInt(0, 50);
//
//					// 上限（最多等 1 秒）
//					sleepTime = Math.min(sleepTime, 1000);
//
//					try {
//						Thread.sleep(sleepTime);
//					} catch (InterruptedException ie) {
//						Thread.currentThread().interrupt(); // 恢復中斷狀態
//					}
//
//					// 繼續下一次 for 迴圈 (即重試)
//					continue;
//				}
//				// --- 如果不是以上衝突錯誤，代表是「邏輯錯誤」(如：金額未達門檻、庫存不足) ---
//				// 直接把錯誤丟出去給前端，不要浪費資源重試
//				throw e;
//			} catch (Exception e) {
//				// 處理非 RuntimeException 的意外錯誤
//				throw new RuntimeException("訂單系統發生非預期錯誤: " + e.getMessage());
//			}
//		}
//		throw new RuntimeException("系統繁忙，請重新結帳");
//	}
//
//	/* 成立訂單: 內部執行方法：負責「查詢庫存 + 查詢最大序號 + 寫入資料庫」。 */
//	// 加上 @Transactional，確保這段動作在資料庫中是原子性的（要嘛全成功，要嘛全失敗）。
//	@Transactional(rollbackFor = Exception.class)
//	public CreateOrdersRes executeInsert(CreateOrdersReq req, String todayStr) {
//
//		// 取的購物車清單
//		List<OrderCartDetails> cartDetailsList = req.getOrderCartDetailsList();
//		// ====== 使用 Map 合併相同 ID 的扣除總量(把商品跟贈品相同的一起計算數量) ======
//		// Key: 產品ID, Value: 總數量 (商品 + 贈品)
//		Map<Integer, Integer> stockToReduceMap = new HashMap<>();
//
//		
//		for (OrderCartDetails detail : cartDetailsList) {
//			stockToReduceMap.merge(detail.getProductId(), detail.getQuantity(), Integer::sum);
//		}
//		// 將 Map 的 Key 轉成 List 並排序，防止不同執行緒因鎖定順序不同而死結 (Deadlock)
//		List<Integer> sortedProductIds = new ArrayList<>(stockToReduceMap.keySet());
//		Collections.sort(sortedProductIds);
//		// 迴圈計算總額時，順便把「所有的贈品 ID」存進這個清單
//		List<Integer> giftProductIds = new ArrayList<>();
//		// 取得該分店所屬國家的稅務設定
//		Regions region = regionsDao.findTaxByAreaId(req.getGlobalAreaId());
//		if (region == null) {
//			throw new RuntimeException("找不到該分店的稅務設定");
//		}
//		BigDecimal taxRate = region.getTaxRate(); // 稅率
//		String taxType = region.getTaxType().name(); // 稅制
//		// 初始化金額 (使用 BigDecimal.ZERO 確保精準度)
//		BigDecimal subtotal = BigDecimal.ZERO;
//		BigDecimal finalSubtotal = BigDecimal.ZERO; // 最終未稅金額
//		BigDecimal taxAmount = BigDecimal.ZERO; // 稅額
//		BigDecimal afterTax = BigDecimal.ZERO; // 含稅
//		BigDecimal totalCost = BigDecimal.ZERO; // 成本價
//		// 取的該分店的所在國家的折扣金額上限
//		Discount discount = discountDao.findByRegionsId(region.getId());
//		if (discount == null) {
//			throw new RuntimeException("找不到該分店所在國家的折扣額度設定");
//		}
//		BigDecimal highestDiscountAmount = BigDecimal.valueOf(discount.getCount());
//		BigDecimal discountOff = BigDecimal.ZERO; // 最終實際折掉的金額
//
//		// ====== 金額計算/贈品id儲存 ======
//		// 不是贈品的才要計算金額 / 贈品的Id要存進贈品清單
//		for (OrderCartDetails detail : cartDetailsList) {
//			// 取的該商品在該分店的價格(未稅)
//			BranchInventory inv = branchInventoryDao
//					.findByProductIdAndGlobalAreaId(detail.getProductId(), req.getGlobalAreaId())
//					.orElseThrow(() -> new RuntimeException("該分店未上架商品 ID: " + detail.getProductId()));
//			BigDecimal qty = BigDecimal.valueOf(detail.getQuantity()); // 取的商品購買數量
//			totalCost = totalCost.add(inv.getCostPrice().multiply(qty)); // 計算成本
//			if (!detail.isGift()) {
//				// 把取的商品金額做迴圈相加，內含稅國家取得的是含稅價格1;外加稅國家取得的是未稅價格
//				subtotal = subtotal.add(inv.getBasePrice().multiply(qty));
//			} else {
//				giftProductIds.add(detail.getProductId());
//			}
//		}
//
//		// 計算初始含稅總金額
//		BigDecimal initialTotal; // 初始含稅總金額
//		if ("INCLUSIVE".equals(taxType)) { // 內含稅本身就是含稅金額
//			initialTotal = subtotal;
//		} else { // 外加稅須把未稅總金額*(1+稅率)
//			initialTotal = subtotal.multiply(BigDecimal.ONE.add(taxRate));
//		}
//
//		// --- 統一計算折扣 (以含稅總額為基準，最公平) ---
//		if (req.isUseDiscount()) {
//			BigDecimal discountMultiplier = new BigDecimal("0.1"); // 折扣掉 10%
//			BigDecimal potentialDiscount = initialTotal.multiply(discountMultiplier); // 取的折扣金額
//
//			// 檢查折扣是否超過上限(最高折扣金額/折扣金額) // BigDecimal 需使用 compareTo 來比較
//			discountOff = potentialDiscount.compareTo(highestDiscountAmount) > 0 ? highestDiscountAmount
//					: potentialDiscount;
//		}
//
//		// --- 算出最終實收金額 ---
//		afterTax = initialTotal.subtract(discountOff).setScale(0, RoundingMode.UP);
//
//		// --- 反推稅額與未稅小計 ---
//		// 公式：稅額 = 總額 - (總額 / (1 + 稅率))
//		// 反推「未稅金額」(總額 / (1 + 稅率))
//		BigDecimal beforeTax = afterTax.divide(BigDecimal.ONE.add(taxRate), 4, RoundingMode.HALF_UP);
//		// 稅額 = 總共付的錢 - 原始餐點的錢
//		taxAmount = afterTax.subtract(beforeTax).setScale(0, RoundingMode.HALF_UP);
//		// 定義「最終未稅小計」
//		finalSubtotal = afterTax.subtract(taxAmount);
//		// [DEBUG] 記錄金額計算結果，這在對帳出錯時非常重要
//		log.debug("【金額計算】購物車: {} -> 最終未稅: {}, 稅額: {}, 含稅: {}, 折扣: {}", //
//				req.getOrderCartId(), finalSubtotal, taxAmount, afterTax, discountOff);
//
//		// ====== 贈品門檻檢查 ======
//		if (!giftProductIds.isEmpty()) { // 判斷贈品清單有沒有資料
//			for (Integer giftId : giftProductIds) {
//				// promotionGiftsDao 可以根據贈品 ID 查門檻
//				BigDecimal giftRule = promotionsGiftsDao //
//						.findFullAmountByGiftProductId(req.getPromotionsId(), giftId);
//
//				if (giftRule != null) { // 如果有取得金額
//					// 2. 直接拿 total 跟這個金額比
//					if (initialTotal.compareTo(giftRule) < 0) { // compareTo：這是 BigDecimal 比較大小的標準寫法
//						// [WARN] 記錄未達標的贈品領取嘗試
//						log.warn("【贈品攔截】購物車: {} 金額 {} 未達門檻 {}", //
//								req.getOrderCartId(), initialTotal, giftRule);
//						throw new RuntimeException("金額未達門檻 " + giftRule + "，無法領取贈品 ID: " + giftId);
//					}
//				}
//			}
//		}
//
//		String newId = ""; // 先宣告變數
//		try {
//
//			// ====== 執行庫存扣除 ======
//			// 依照排序後的 ID 逐一處理，每個產品 ID 只會執行一次資料庫更新
//			for (int productId : sortedProductIds) {
//				// 根據key(productId)，取得 使用者想買的數量(含贈品)
//				int quantityToBuy = stockToReduceMap.get(productId);
//				// 根據商品 id 分店 id 去商品表搜尋庫存
//				Products product = productsDao.findById(productId);
//				// 1. 查詢該分店的庫存快照
//				BranchInventory inv = branchInventoryDao //
//						.findByProductIdAndGlobalAreaId(productId, req.getGlobalAreaId()) //
//						.orElseThrow(() -> new RuntimeException("找不到分店庫存資料"));
//				// 庫存檢查：如果庫存 比要買的數量還少
//				if (inv.getStockQuantity() < quantityToBuy) {
//					// 拋出例外後，事務會自動回滾，前面扣掉的其他商品庫存也會還回去
//					// return new CreateOrdersRes(ReplyMessage.STOCK_NOT_ENOUGH.getCode(),
//					// ReplyMessage.STOCK_NOT_ENOUGH.getMessage());
//					throw new RuntimeException("商品「" + product.getName() + "」庫存不足");
//				}
//				// 取得舊的版本號 (這就是你要帶入 SQL 的 ?3)
//				int oldVersion = inv.getVersion();
//				// 執行「手動樂觀鎖」更新
//				int affectedRows = branchInventoryDao //
//						.updateBranchStock(productId, req.getGlobalAreaId(), quantityToBuy, oldVersion);
//				// 如果回傳 0，代表這期間 version 被動過，拋出異常觸發外層重試
//				if (affectedRows == 0) {
//					throw new RuntimeException("庫存版本衝突，準備重試");
//				}
//			}
//
//			// ====== 執行贈品配額扣除 ======
//			if (!giftProductIds.isEmpty()) {
//				for (Integer giftId : giftProductIds) {
//					// 執行原子扣除
//					int affectedGiftRows = promotionsGiftsDao.reduceGiftQuota(req.getPromotionsId(), giftId);
//
//					if (affectedGiftRows == 0) {
//						// 代表這秒鐘剛好被別人領完了
//						throw new RuntimeException("很抱歉，贈品已全數兌換完畢");
//					}
//				}
//			}
//			
//			// 💡 新增：將 yyyyMMdd 字串轉回 LocalDateTime
//	        // 假設固定在當天的中午 12 點，或者你可以再加隨機秒數
//	        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
//	        LocalDate date = LocalDate.parse(todayStr, formatter);
//	        LocalDateTime randomCompletedAt = date.atTime(
//	            faker.number().numberBetween(9, 21), // 隨機 9點~21點
//	            faker.number().numberBetween(0, 59), 
//	            faker.number().numberBetween(0, 59)
//	        );
//
//			// ====== 產生新訂單編號 (悲觀鎖排隊入口) ======
//			// 去資料庫找今天最後一筆訂單 (DAO 裡面要有 ORDER BY id DESC LIMIT 1)
//			Optional<Orders> lastOrder = ordersDao.getOrderByOrderDateId(todayStr);
//			int nextSeq = 1; // 預設從 1 開始
//			if (lastOrder.isPresent()) {
//				// 如果今天有訂單，把最大的序號轉成數字並 +1
//				nextSeq = Integer.parseInt(lastOrder.get().getId()) + 1;
//			}
//			// 將數字格式化為 4 位字串，例如 1 變成 "0001"
//			newId = String.format("%04d", nextSeq);
//
//			// ====== 執行新增主訂單 ======
//			ordersDao.insertTest(newId, todayStr, req.getOrderCartId(), req.getGlobalAreaId(), req.getMemberId(), //
//					req.getPhone(), finalSubtotal, taxAmount, afterTax, totalCost, "CASH", //
//					"PICKED_UP", "PAID", randomCompletedAt, req.isUseDiscount());
//			log.info("【產單成功】購物車: {} -> 訂單編號: {}-{}", req.getOrderCartId(), todayStr, newId);
//
//			// 成功後回傳結果
//			return new CreateOrdersRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(), //
//					newId, todayStr, afterTax);
//		} catch (Exception e) {
//			// [ERROR] 記錄詳細的資料庫操作失敗原因
//			log.error("【資料庫異常】訂單寫入失敗，購物車 ID: {}, 錯誤: {}", req.getOrderCartId(), e.getMessage());
//			System.out.println("executeInsert 執行失敗，準備回滾並交由外層判斷: " + e.getMessage());
//			// // 關鍵】因為我們想 return 包裝好的 JSON，所以必須手動標記回滾
//			// TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
//			// // 回傳友善訊息給前端
//			// return new CreateOrdersRes(500, "操作失敗：" + e.getMessage());
//			throw e;
//		}
//	}
//}
