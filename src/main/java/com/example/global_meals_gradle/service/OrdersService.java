package com.example.global_meals_gradle.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.global_meals_gradle.constants.OrdersStatus;
import com.example.global_meals_gradle.constants.PayStatus;
import com.example.global_meals_gradle.constants.ReplyMessage;
import com.example.global_meals_gradle.controller.MembersController;
import com.example.global_meals_gradle.controller.StaffController;
import com.example.global_meals_gradle.dao.BranchInventoryDao;
import com.example.global_meals_gradle.dao.DiscountDao;
import com.example.global_meals_gradle.dao.MembersDao;
import com.example.global_meals_gradle.dao.OrdersDao;
import com.example.global_meals_gradle.dao.ProductsDao;
import com.example.global_meals_gradle.dao.PromotionsGiftsDao;
import com.example.global_meals_gradle.dao.RegionsDao;
import com.example.global_meals_gradle.entity.BranchInventory;
import com.example.global_meals_gradle.entity.Discount;
import com.example.global_meals_gradle.entity.Members;
import com.example.global_meals_gradle.entity.OrderCartDetails;
import com.example.global_meals_gradle.entity.Orders;
import com.example.global_meals_gradle.entity.Products;
import com.example.global_meals_gradle.entity.Regions;
import com.example.global_meals_gradle.entity.Staff;
import com.example.global_meals_gradle.req.CashPayOnSiteReq;
import com.example.global_meals_gradle.req.CreateOrdersReq;
import com.example.global_meals_gradle.req.PayForPOSReq;
import com.example.global_meals_gradle.req.PayReq;
import com.example.global_meals_gradle.req.UpdateOrdersStatusReq;
import com.example.global_meals_gradle.res.BasicRes;
import com.example.global_meals_gradle.res.CreateOrdersRes;
import com.example.global_meals_gradle.res.GetAllOrdersRes;
import com.example.global_meals_gradle.res.GetAllOrdersUncompleteRes;
import com.example.global_meals_gradle.res.GetAllOrdersUncompleteRes.OrderInfo;
import com.example.global_meals_gradle.res.MembersRes;
import com.example.global_meals_gradle.vo.GetOrdersDetailVo;
import com.example.global_meals_gradle.vo.GetOrdersVo;

import jakarta.servlet.http.HttpSession;

@Service
public class OrdersService {

	private static final org.slf4j.Logger log = org.slf4j //
			.LoggerFactory.getLogger(OrdersService.class);

	@Autowired
	private OrdersDao ordersDao;

	@Autowired
	private ProductsDao productsDao;

	@Autowired
	private MembersDao membersDao;

	@Autowired
	private PromotionsGiftsDao promotionsGiftsDao;

	@Autowired
	private RegionsDao regionsDao;

	@Autowired
	private BranchInventoryDao branchInventoryDao;

	@Autowired
	private DiscountDao discountDao;

	// 這樣在呼叫 self.executeInsert 時，Spring 才會啟動 @Transactional 的代理機制。
	@Autowired
	@Lazy // 加上 @Lazy 避免某些 Spring 版本出現循環依賴的警告
	private OrdersService self;

	/* 取的該會員的今天訂單 */
	@Transactional(readOnly = true) // 只有查詢，寫這段對效能比較好
	public GetAllOrdersUncompleteRes getTodayOrdersListByMember(HttpSession httpSession) {
		// 抓會員資訊(因為會員登入那邊存的是res，所以會多一層)
		MembersRes membersRes = (MembersRes) httpSession.getAttribute(MembersController.ATTRIBUTE_KEY);
		Members member = (membersRes != null) ? membersRes.getMembers() : null;
		if (member == null) {
			return new GetAllOrdersUncompleteRes(ReplyMessage.NOT_LOGIN.getCode(), ReplyMessage.NOT_LOGIN.getMessage(),
					null);
		}
		// 取的今天的日期字串，參考成立訂單
		String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

		List<Object[]> rawData = ordersDao.GetOrdersUncomplete(todayStr, member.getId());

		List<OrderInfo> ordersList = new ArrayList<>();
		if (rawData != null) {
			for (Object[] row : rawData) {
				OrderInfo info = new OrderInfo();
				info.setId(String.valueOf(row[0])); // 對應 SQL 的 id
				info.setOrderDateId(String.valueOf(row[1])); // 對應 SQL 的 order_date_id
				info.setOrderStatus(String.valueOf(row[2])); // 對應 SQL 的 orders_status
				ordersList.add(info);
			}
		}

		return new GetAllOrdersUncompleteRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(),
				ordersList);

	}

	/* 查詢該分店今日所有訂單 */
	@Transactional(readOnly = true) // 只有查詢，寫這段對效能比較好
	public GetAllOrdersRes getTodayAllOrders(HttpSession httpSession) {
		// 抓員工資訊
		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
		if (staff == null) { // 只有管理者才能查詢
			return new GetAllOrdersRes(ReplyMessage.PERMISSION_DENIED.getCode(),
					ReplyMessage.PERMISSION_DENIED.getMessage());
		}
		// 取的今天的日期字串，參考成立訂單
		String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		// 取得所在分店
		int globalAreaId = staff.getGlobalAreaId();
		List<Object[]> rawData = ordersDao.getTodayAllOrders(todayStr, globalAreaId);
		// 用 Map 來群組化，Key 是 "日期+ID"，Value 是訂單 VO
		// LinkedHashMap 是為了「照順序排」，讓最新下單的排在最前面
		Map<String, GetOrdersVo> orderMap = new LinkedHashMap<>();
		for (Object[] row : rawData) {
			String orderKey = row[1].toString() + row[0].toString(); // 日期 + ID

			GetOrdersVo vo = orderMap.computeIfAbsent(orderKey, k -> {
				// 建立一個新的訂單物件
				GetOrdersVo newVo = new GetOrdersVo();
				newVo.setId(row[0] != null ? row[0].toString() : ""); // o.id
				newVo.setOrderDateId(row[1] != null ? row[1].toString() : ""); // o.order_date_id
				newVo.setGlobalAreaId(row[2] != null ? ((Number) row[2]).intValue() : 0); // o.global_area_id
				newVo.setTotalAmount(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO); // o.total_amount
				newVo.setOrdersStatus(row[4] != null ? row[4].toString() : "UNKNOWN"); // o.status
				newVo.setPayStatus(row[5] != null ? row[5].toString() : "UNKNOWN");// o.status
				// 大部分的 JDBC 驅動（如 MySQL Connector/J）在處理資料庫的 DATETIME 或 TIMESTAMP 欄位時，
				// 回傳的 Java 物件實際上是 java.sql.Timestamp
				// 必須先轉成 Timestamp，再呼叫它內建的轉換方法 .toLocalDateTime()
				newVo.setCompletedAt(row[6] != null ? (LocalDateTime) row[6] : null);
				newVo.setGetOrdersDetailVoList(new ArrayList<>());
				return newVo;
			});

			// 建立明細並塞入該訂單的 List
			GetOrdersDetailVo detail = new GetOrdersDetailVo();
			// 如果是寫 Integer ，DB 回傳是：BigInteger/Long，會直接噴 ClassCastException
			detail.setQuantity(row[7] != null ? ((Number) row[7]).intValue() : 0);
			detail.setPrice(row[8] != null ? (BigDecimal) row[8] : BigDecimal.ZERO);
			detail.setGift(row[9] != null && ((Number) row[9]).intValue() == 1); // 回傳0或1，在 Java 是數字，透過比較運算產生 true/false
			detail.setDiscountNote(row[10] != null ? row[10].toString() : "");
			detail.setName(row[11] != null ? row[11].toString() : "未知商品"); // 產品名稱已經在 SQL 抓好了

			vo.getGetOrdersDetailVoList().add(detail);
		}
		// 把 Map 的值轉換成 List
		List<GetOrdersVo> result = new ArrayList<>(orderMap.values());

		return new GetAllOrdersRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(), result);

	}

	/* 查詢歷史訂單(有分員工或會員查詢) */
	@Transactional(readOnly = true) // 只有查詢，寫這段對效能比較好
	public GetAllOrdersRes getAllOrders(Integer memberId, HttpSession httpSession) {
		// 抓員工資訊
		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
		// 抓會員資訊(因為會員登入那邊存的是res，所以會多一層)
		MembersRes membersRes = (MembersRes) httpSession.getAttribute(MembersController.ATTRIBUTE_KEY);
		Members member = (membersRes != null) ? membersRes.getMembers() : null;
		if (staff != null) { // 代表是員工操作 // MemberId是設Integer，如果是int就要判斷是否 ==0
			if (memberId == null || membersDao.findById(memberId) == null) {
				return new GetAllOrdersRes(ReplyMessage.MEMBER_NOT_FOUND.getCode(),
						ReplyMessage.MEMBER_NOT_FOUND.getMessage());
			}
		} else if (member != null) { // 代表會員操作，只能查自己的歷史訂單紀錄
			memberId = member.getId();
		} else {
			// 既不是員工也不是會員 -> 先登入才能查詢歷史訂單
			return new GetAllOrdersRes(ReplyMessage.NOT_LOGIN.getCode(), //
					ReplyMessage.NOT_LOGIN.getMessage());
		}
		List<Object[]> rawData = ordersDao.getFullOrderHistory(memberId);
		// 用 Map 來群組化，Key 是 "日期+ID"，Value 是訂單 VO
		// LinkedHashMap 是為了「照順序排」，讓最新下單的排在最前面
		Map<String, GetOrdersVo> orderMap = new LinkedHashMap<>();
		for (Object[] row : rawData) {
			String orderKey = row[1].toString() + row[0].toString(); // 日期 + ID

			// 如果這個訂單還沒在 Map 裡，先建立它
			// 【補充說明：Lambda 表達式】
			// 這裡的 k -> { ... } 是一個 Lambda 表達式。
			// computeIfAbsent 的意思是：如果 orderMap 裡面還沒有這個 orderKey，
			// 就會自動執行後面這個 Lambda 裡面的程式碼，把回傳的 newVo 存進 Map 中並回傳給左邊的 vo。
			// 代替以前一定要寫 if (map.get(key) == null) 的麻煩判斷。
			// 以前寫法:
			// GetOrdersVo vo = orderMap.get(orderKey);
			// if (vo == null) {
			// vo = new GetOrdersVo();
			// orderMap.put(orderKey, vo);
			// }
			GetOrdersVo vo = orderMap.computeIfAbsent(orderKey, k -> {
				// 建立一個新的訂單物件
				GetOrdersVo newVo = new GetOrdersVo();
				newVo.setId(row[0] != null ? row[0].toString() : ""); // o.id
				newVo.setOrderDateId(row[1] != null ? row[1].toString() : ""); // o.order_date_id
				newVo.setGlobalAreaId(row[2] != null ? ((Number) row[2]).intValue() : 0); // o.global_area_id
				newVo.setTotalAmount(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO); // o.total_amount
				newVo.setOrdersStatus(row[4] != null ? row[4].toString() : "UNKNOWN");  // o.status
				newVo.setPayStatus(row[5] != null ? row[5].toString() : "UNKNOWN");// o.status
				// 大部分的 JDBC 驅動（如 MySQL Connector/J）在處理資料庫的 DATETIME 或 TIMESTAMP 欄位時，
				// 回傳的 Java 物件實際上是 java.sql.Timestamp
				// 必須先轉成 Timestamp，再呼叫它內建的轉換方法 .toLocalDateTime()
				newVo.setCompletedAt(row[6] != null ? (LocalDateTime) row[6] : null);
				newVo.setGetOrdersDetailVoList(new ArrayList<>());
				return newVo;
			});

			// 建立明細並塞入該訂單的 List
			GetOrdersDetailVo detail = new GetOrdersDetailVo();
			// 如果是寫 Integer ，DB 回傳是：BigInteger/Long，會直接噴 ClassCastException
			detail.setQuantity(row[7] != null ? ((Number) row[7]).intValue() : 0);
			detail.setPrice(row[8] != null ? (BigDecimal) row[8] : BigDecimal.ZERO);
			detail.setGift(row[9] != null && ((Number) row[9]).intValue() == 1); // 回傳0或1，在 Java 是數字，透過比較運算產生 true/false
			detail.setDiscountNote(row[10] != null ? row[10].toString() : "");
			detail.setName(row[11] != null ? row[11].toString() : "未知商品"); // 產品名稱已經在 SQL 抓好了

			vo.getGetOrdersDetailVoList().add(detail);
		}
		// 把 Map 的值轉換成 List
		List<GetOrdersVo> result = new ArrayList<>(orderMap.values());

		return new GetAllOrdersRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), result);
	}

	/* 成立訂單: 外部呼叫的主入口：負責「高併發重試流程」 */
	// 這個方法「不加」@Transactional，這樣裡面的 try-catch 才能重複執行。
	public CreateOrdersRes createOrders(CreateOrdersReq req, HttpSession httpSession) {
		// 抓員工資訊
		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
		// 抓會員資訊(因為會員登入那邊存的是res，所以會多一層)
		MembersRes membersRes = (MembersRes) httpSession.getAttribute(MembersController.ATTRIBUTE_KEY);
		Members member = (membersRes != null) ? membersRes.getMembers() : null;
		if (staff != null) { // 代表是員工操作
			if (req.getMemberId() <= 0) {
				return new CreateOrdersRes(ReplyMessage.MEMBER_NOT_FOUND.getCode(),
						ReplyMessage.MEMBER_NOT_FOUND.getMessage());
			}
			if (req.getMemberId() > 1 && membersDao.findById(req.getMemberId()) == null) {
				return new CreateOrdersRes(ReplyMessage.MEMBER_NOT_FOUND.getCode(),
						ReplyMessage.MEMBER_NOT_FOUND.getMessage());
			}
		} else if (member != null) { // 代表會員操作
			req.setMemberId(member.getId());
		} else { // 代表是遊客
			if (req.getMemberId() > 1) { // 可能是會員，但session失效(登出) "連線已逾時，請重新登入後再結帳"
				return new CreateOrdersRes(ReplyMessage.NOT_LOGIN.getCode(), //
						ReplyMessage.NOT_LOGIN.getMessage());
			}
			req.setMemberId(1);
		}

		// [DEBUG] 記錄請求進入，方便追蹤
		log.debug("【訂單請求】收到購物車 ID: {}, 會員 ID: {}", req.getOrderCartId(), req.getMemberId());

		if (req.isUseDiscount()) {
			if (req.getMemberId() == 1) {
				// [WARN] 記錄異常的折扣請求（可能是前端繞過或邏輯錯誤）
				log.warn("【訂單攔截】購物車id {} 嘗試使用折扣但資格不符", req.getOrderCartId());
				throw new RuntimeException("無優惠可使用");
			}
			Members memberForDiscount = membersDao.findById(req.getMemberId());
			if (memberForDiscount == null || !memberForDiscount.isDiscount()) {
				// [WARN] 記錄異常的折扣請求（可能是前端繞過或邏輯錯誤）
				log.warn("【訂單攔截】會員 {} 嘗試使用折扣但資格不符", req.getMemberId());
				throw new RuntimeException("無優惠可使用");
			}
		}
		if (ordersDao.existsByOrderCartId(req.getOrderCartId())) {
			throw new RuntimeException("該購物車已轉換為訂單，請勿重複提交");
		}

		// 取得今天的日期字串，例如 "20260328"
		// DateTimeFormatter.ofPattern("yyyyMMdd"): 定義日期格式 .format(...):
		// 把得到的日期轉換成前面定義的格式
		String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

		// 設定最大重試次數。如果很多人同時搶號碼，失敗了就重新跑一次迴圈。
		int maxRetries = 5;

		for (int i = 0; i < maxRetries; i++) {
			try {
				// 必須透過 self. 呼叫，否則事務 (@Transactional) 會失效！
				return self.executeInsert(req, todayStr);
			} catch (RuntimeException e) {
				// 判斷是否為「可重試」的異常
				String msg = e.getMessage();
				// 購物車Id在資料庫有設UQ，所以如果連續點擊，會有錯誤訊息，到這裡就會傳送錯誤訊息給前端
				if (msg != null && msg.contains("order_cart_id")) {
					throw new RuntimeException("該購物車已轉換為訂單，請勿重複提交");
				}
				// 只有當訊息包含「衝突」(樂觀鎖失敗) 或 「Duplicate」(序號重複) 時才進入重試
				if (msg != null && (msg.contains("衝突") || msg.contains("Duplicate")//
						|| msg.contains("Primary"))) {
					log.info("【訂單重試】購物車 ID: {} 發生衝突，準備進行第 {} 次重試... 原因: {}", //
							req.getOrderCartId(), i + 1, msg);
					// 如果還沒超過重試次數，就繼續跑下一輪 for 迴圈。
					if (i == maxRetries - 1) {
						// 如果重試了 5 次都還是失敗，才拋出錯誤。
						// [ERROR] 記錄重試耗盡，這代表系統併發極高，可能需要優化
						log.error("【訂單失敗】購物車 ID: {} 重試 {} 次後仍失敗", //
								req.getOrderCartId(), maxRetries);
						throw new RuntimeException("系統繁忙，請重新結帳");
					}

					// 指數退避: 越多次，時間越久
					long sleepTime = 50 * (long) Math.pow(2, i);

					// 加隨機 jitter（0~50ms）
					sleepTime += ThreadLocalRandom.current().nextInt(0, 50);

					// 上限（最多等 1 秒）
					sleepTime = Math.min(sleepTime, 1000);

					try {
						Thread.sleep(sleepTime);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt(); // 恢復中斷狀態
					}

					// 繼續下一次 for 迴圈 (即重試)
					continue;
				}
				// --- 如果不是以上衝突錯誤，代表是「邏輯錯誤」(如：金額未達門檻、庫存不足) ---
				// 直接把錯誤丟出去給前端，不要浪費資源重試
				throw e;
			} catch (Exception e) {
				// 處理非 RuntimeException 的意外錯誤
				throw new RuntimeException("訂單系統發生非預期錯誤: " + e.getMessage());
			}
		}
		throw new RuntimeException("系統繁忙，請重新結帳");
	}

	/* 成立訂單: 內部執行方法：負責「查詢庫存 + 查詢最大序號 + 寫入資料庫」。 */
	// 加上 @Transactional，確保這段動作在資料庫中是原子性的（要嘛全成功，要嘛全失敗）。
	@Transactional(rollbackFor = Exception.class)
	public CreateOrdersRes executeInsert(CreateOrdersReq req, String todayStr) {

		// 取的購物車清單
		List<OrderCartDetails> cartDetailsList = req.getOrderCartDetailsList();
		// ====== 使用 Map 合併相同 ID 的扣除總量(把商品跟贈品相同的一起計算數量) ======
		// Key: 產品ID, Value: 總數量 (商品 + 贈品)
		Map<Integer, Integer> stockToReduceMap = new HashMap<>();

		// 這是一般的 if-else 寫法
		// for (OrderCartDetails detail : cartDetailsList) {
		// int pid = detail.getProductId();
		// int qty = detail.getQuantity();
		// 檢查 Map 裡是不是已經有這個商品 ID 了
		// if (stockToReduceMap.containsKey(pid)) { // 情況A】：已經有了（例如之前跑過商品，現在跑贈品）
		// int oldQty = stockToReduceMap.get(pid); // 取出舊的數量
		// stockToReduceMap.put(pid, oldQty + qty); // 加完後更新進去
		// } else { // 【情況 B】：還沒出現過
		// stockToReduceMap.put(pid, qty); // 直接放進去
		// }
		// }

		/*
		 * merge 方法的三個參數意義： detail.getProductId()：我要找哪個 ID？ detail.getQuantity()：如果這個 ID
		 * 第一次出現，預設值(這裡是指數量)是多少？ Integer::sum：如果這個 ID 已經存在了，舊的值跟新的值要怎麼處理？（這裡指定用
		 * sum，也就是「加起來」）
		 */
		for (OrderCartDetails detail : cartDetailsList) {
			stockToReduceMap.merge(detail.getProductId(), detail.getQuantity(), Integer::sum);
		}
		// 將 Map 的 Key 轉成 List 並排序，防止不同執行緒因鎖定順序不同而死結 (Deadlock)
		List<Integer> sortedProductIds = new ArrayList<>(stockToReduceMap.keySet());
		Collections.sort(sortedProductIds);
		// 迴圈計算總額時，順便把「所有的贈品 ID」存進這個清單
		List<Integer> giftProductIds = new ArrayList<>();
		// 取得該分店所屬國家的稅務設定
		Regions region = regionsDao.findTaxByAreaId(req.getGlobalAreaId());
		if (region == null) {
			throw new RuntimeException("找不到該分店的稅務設定");
		}
		BigDecimal taxRate = region.getTaxRate(); // 稅率
		String taxType = region.getTaxType().name(); // 稅制
		// 初始化金額 (使用 BigDecimal.ZERO 確保精準度)
		BigDecimal subtotal = BigDecimal.ZERO;
		BigDecimal finalSubtotal = BigDecimal.ZERO; // 最終未稅金額
		BigDecimal taxAmount = BigDecimal.ZERO; // 稅額
		BigDecimal afterTax = BigDecimal.ZERO; // 含稅
		BigDecimal totalCost = BigDecimal.ZERO; // 成本價
		// 取的該分店的所在國家的折扣金額上限
		Discount discount = discountDao.findByRegionsId(region.getId());
		if (discount == null) {
			throw new RuntimeException("找不到該分店所在國家的折扣額度設定");
		}
		BigDecimal highestDiscountAmount = BigDecimal.valueOf(discount.getCount());
		BigDecimal discountOff = BigDecimal.ZERO; // 最終實際折掉的金額

		// ====== 金額計算/贈品id儲存 ======
		// 不是贈品的才要計算金額 / 贈品的Id要存進贈品清單
		for (OrderCartDetails detail : cartDetailsList) {
			// 取的該商品在該分店的價格(未稅)
			BranchInventory inv = branchInventoryDao
					.findByProductIdAndGlobalAreaId(detail.getProductId(), req.getGlobalAreaId())
					.orElseThrow(() -> new RuntimeException("該分店未上架商品 ID: " + detail.getProductId()));
			BigDecimal qty = BigDecimal.valueOf(detail.getQuantity()); // 取的商品購買數量
			totalCost = totalCost.add(inv.getCostPrice().multiply(qty)); // 計算成本
			if (!detail.isGift()) {
				// 把取的商品金額做迴圈相加，內含稅國家取得的是含稅價格1;外加稅國家取得的是未稅價格
				subtotal = subtotal.add(inv.getBasePrice().multiply(qty));
			} else {
				giftProductIds.add(detail.getProductId());
			}
		}

		// 計算初始含稅總金額
		BigDecimal initialTotal; // 初始含稅總金額
		if ("INCLUSIVE".equals(taxType)) { // 內含稅本身就是含稅金額
			initialTotal = subtotal;
		} else { // 外加稅須把未稅總金額*(1+稅率)
			initialTotal = subtotal.multiply(BigDecimal.ONE.add(taxRate));
		}

		// --- 統一計算折扣 (以含稅總額為基準，最公平) ---
		if (req.isUseDiscount()) {
			BigDecimal discountMultiplier = new BigDecimal("0.1"); // 折扣掉 10%
			BigDecimal potentialDiscount = initialTotal.multiply(discountMultiplier); // 取的折扣金額

			// 檢查折扣是否超過上限(最高折扣金額/折扣金額) // BigDecimal 需使用 compareTo 來比較
			discountOff = potentialDiscount.compareTo(highestDiscountAmount) > 0 ? highestDiscountAmount
					: potentialDiscount;
		}

		// --- 算出最終實收金額 ---
		afterTax = initialTotal.subtract(discountOff).setScale(0, RoundingMode.UP);

		// --- 反推稅額與未稅小計 ---
		// 公式：稅額 = 總額 - (總額 / (1 + 稅率))
		// 反推「未稅金額」(總額 / (1 + 稅率))
		BigDecimal beforeTax = afterTax.divide(BigDecimal.ONE.add(taxRate), 4, RoundingMode.HALF_UP);
		// 稅額 = 總共付的錢 - 原始餐點的錢
		taxAmount = afterTax.subtract(beforeTax).setScale(0, RoundingMode.HALF_UP);
		// 定義「最終未稅小計」
		finalSubtotal = afterTax.subtract(taxAmount);
		// [DEBUG] 記錄金額計算結果，這在對帳出錯時非常重要
		log.debug("【金額計算】購物車: {} -> 最終未稅: {}, 稅額: {}, 含稅: {}, 折扣: {}", //
				req.getOrderCartId(), finalSubtotal, taxAmount, afterTax, discountOff);

		// ====== 贈品門檻檢查 ======
		if (!giftProductIds.isEmpty()) { // 判斷贈品清單有沒有資料
			for (Integer giftId : giftProductIds) {
				// promotionGiftsDao 可以根據贈品 ID 查門檻
				BigDecimal giftRule = promotionsGiftsDao //
						.findFullAmountByGiftProductId(req.getPromotionsId(), giftId);

				if (giftRule != null) { // 如果有取得金額
					// 2. 直接拿 total 跟這個金額比
					if (initialTotal.compareTo(giftRule) < 0) { // compareTo：這是 BigDecimal 比較大小的標準寫法
						// [WARN] 記錄未達標的贈品領取嘗試
						log.warn("【贈品攔截】購物車: {} 金額 {} 未達門檻 {}", //
								req.getOrderCartId(), initialTotal, giftRule);
						throw new RuntimeException("金額未達門檻 " + giftRule + "，無法領取贈品 ID: " + giftId);
					}
				}
			}
		}

		String newId = ""; // 先宣告變數
		try {

			// ====== 執行庫存扣除 ======
			// 依照排序後的 ID 逐一處理，每個產品 ID 只會執行一次資料庫更新
			for (int productId : sortedProductIds) {
				// 根據key(productId)，取得 使用者想買的數量(含贈品)
				int quantityToBuy = stockToReduceMap.get(productId);
				// 根據商品 id 分店 id 去商品表搜尋庫存
				Products product = productsDao.findById(productId);
				// 1. 查詢該分店的庫存快照
				BranchInventory inv = branchInventoryDao //
						.findByProductIdAndGlobalAreaId(productId, req.getGlobalAreaId()) //
						.orElseThrow(() -> new RuntimeException("找不到分店庫存資料"));
				// 庫存檢查：如果庫存 比要買的數量還少
				if (inv.getStockQuantity() < quantityToBuy) {
					// 拋出例外後，事務會自動回滾，前面扣掉的其他商品庫存也會還回去
					// return new CreateOrdersRes(ReplyMessage.STOCK_NOT_ENOUGH.getCode(),
					// ReplyMessage.STOCK_NOT_ENOUGH.getMessage());
					throw new RuntimeException("商品「" + product.getName() + "」庫存不足");
				}
				// 取得舊的版本號 (這就是你要帶入 SQL 的 ?3)
				int oldVersion = inv.getVersion();
				// 執行「手動樂觀鎖」更新
				int affectedRows = branchInventoryDao //
						.updateBranchStock(productId, req.getGlobalAreaId(), quantityToBuy, oldVersion);
				// 如果回傳 0，代表這期間 version 被動過，拋出異常觸發外層重試
				if (affectedRows == 0) {
					throw new RuntimeException("庫存版本衝突，準備重試");
				}
			}

			// ====== 執行贈品配額扣除 ======
			if (!giftProductIds.isEmpty()) {
				for (Integer giftId : giftProductIds) {
					// 執行原子扣除
					int affectedGiftRows = promotionsGiftsDao.reduceGiftQuota(req.getPromotionsId(), giftId);

					if (affectedGiftRows == 0) {
						// 代表這秒鐘剛好被別人領完了
						throw new RuntimeException("很抱歉，贈品已全數兌換完畢");
					}
				}
			}

			// ====== 產生新訂單編號 (悲觀鎖排隊入口) ======
			// 去資料庫找今天最後一筆訂單 (DAO 裡面要有 ORDER BY id DESC LIMIT 1)
			Optional<Orders> lastOrder = ordersDao.getOrderByOrderDateId(todayStr);
			int nextSeq = 1; // 預設從 1 開始
			if (lastOrder.isPresent()) {
				// 如果今天有訂單，把最大的序號轉成數字並 +1
				nextSeq = Integer.parseInt(lastOrder.get().getId()) + 1;
			}
			// 將數字格式化為 4 位字串，例如 1 變成 "0001"
			newId = String.format("%04d", nextSeq);

			// ====== 執行新增主訂單 ======
			ordersDao.insert(newId, todayStr, req.getOrderCartId(), req.getGlobalAreaId(), req.getMemberId(), //
					req.getPhone(), finalSubtotal, taxAmount, afterTax, totalCost, //
					"PREPARING", "UNPAID", req.isUseDiscount());
			log.info("【產單成功】購物車: {} -> 訂單編號: {}-{}", req.getOrderCartId(), todayStr, newId);

			// 成功後回傳結果
			return new CreateOrdersRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(), //
					newId, todayStr, afterTax);
		} catch (Exception e) {
			// [ERROR] 記錄詳細的資料庫操作失敗原因
			log.error("【資料庫異常】訂單寫入失敗，購物車 ID: {}, 錯誤: {}", req.getOrderCartId(), e.getMessage());
			System.out.println("executeInsert 執行失敗，準備回滾並交由外層判斷: " + e.getMessage());
			//			 // 關鍵】因為我們想 return 包裝好的 JSON，所以必須手動標記回滾
			//			 TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			//			 // 回傳友善訊息給前端
			//			 return new CreateOrdersRes(500, "操作失敗：" + e.getMessage());
			throw e;
		}
	}

	/* 付款完成新增(更新)的資料(付款方式、交易號碼、狀態) */
	@Transactional(rollbackFor = Exception.class)
	public BasicRes pay(PayReq req, HttpSession httpSession) {
		// 抓員工資訊
		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
		// 參數檢查(有在Req那裏自動檢查)
		Orders order = ordersDao.getOrderByOrderDateIdAndId(req.getOrderDateId(), req.getId());
		if (order == null) {
			return new BasicRes(ReplyMessage.ORDER_NUMBER_NOT_FOUND.getCode(), //
					ReplyMessage.ORDER_NUMBER_NOT_FOUND.getMessage());
		}
		// 金額檢查: 使用 compareTo == 0 來比對 BigDecimal，避免 100.0 跟 100 判定不一致的問題
		if (order.getTotalAmount().compareTo(req.getTotalAmount()) != 0) {
			throw new RuntimeException("支付金額與訂單金額不符！");
		}
		// 檢查訂單狀態：只有「未付款」的訂單可以執行付款
		// 如果訂單已經是 PAID, 則不需要重複付款
		if (PayStatus.PAID.name().equals(order.getPayStatus().name())) {
			return new BasicRes(ReplyMessage.SUCCESS.getCode(), "訂單已支付完成，無需重複操作");
		}
		try {
			// 如果線上選現金付款，會走這裡，不會繼續往下走
			if (req.getPaymentMethod().equalsIgnoreCase("CASH") && staff == null) {
				log.info("線上點餐臨櫃付款，訂單編號: {} - {}", //
						req.getOrderDateId(), req.getId());
				int result = ordersDao.updatePaymentMethod(req.getId(), req.getOrderDateId(), //
						req.getPaymentMethod(), PayStatus.UNPAID.getPayStatus());
				if (result > 0) {
					return new BasicRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
				}
				throw new RuntimeException("付款更新失敗");
			}
			// 新增(更新)的資料(付款方式、交易號碼、狀態)
			int result = ordersDao.updatePay(req.getId(), req.getOrderDateId(), //
					req.getPaymentMethod(), req.getTransactionId(), "PAID");
			if (result == 0) {
				throw new RuntimeException("付款更新失敗");
			}
			processMemberLoyalty(order);
			return new BasicRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
		} catch (RuntimeException e) {
			// // 關鍵】因為我們想 return 包裝好的 JSON，所以必須手動標記回滾
			// TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			// // 回傳友善訊息給前端
			// return new BasicRes(500, "操作失敗：" + e.getMessage());

			throw new RuntimeException("付款失敗：" + e.getMessage());
		} catch (Exception e) {
			log.error("非預期錯誤: ", e);
			throw new RuntimeException("系統錯誤，請洽工程人員" + e.getMessage());
		}
	}

	/* 線上訂餐選擇現金付款，到現場付款的方法 */
	@Transactional
	public BasicRes cashPayOnSite(CashPayOnSiteReq req, HttpSession httpSession) {
		try {
			// 抓員工資訊
			Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
			if (staff == null) { // 只有管理者才能查詢
				return new BasicRes(ReplyMessage.PERMISSION_DENIED.getCode(),
						ReplyMessage.PERMISSION_DENIED.getMessage());
			}
			Orders order = ordersDao.getOrderByOrderDateIdAndId(req.getOrderDateId(), req.getId());
			if (order == null) {
				return new BasicRes(ReplyMessage.ORDER_NOT_FOUND.getCode(), ReplyMessage.ORDER_NOT_FOUND.getMessage());
			}
			// 如果該筆訂單所選的分店，與去取餐的分店是不同，需報錯
			if (staff.getGlobalAreaId() != order.getGlobalAreaId()) {
				return new BasicRes(ReplyMessage.BRANCHES_DIFFERENT.getCode(),
						ReplyMessage.BRANCHES_DIFFERENT.getMessage());
			}
			// 如果該筆訂單不是未付款，則會報錯
			if (!PayStatus.UNPAID.name().equalsIgnoreCase(order.getPayStatus().name())) {
				return new BasicRes(ReplyMessage.PAY_STATUS_ERROR.getCode(),
						ReplyMessage.PAY_STATUS_ERROR.getMessage());
			}
			// 檢查金額
			if (order.getTotalAmount().compareTo(req.getTotalAmount()) != 0) {
				return new BasicRes(ReplyMessage.TOTAL_AMOUNT_ERROR.getCode(),
						ReplyMessage.TOTAL_AMOUNT_ERROR.getMessage());
			}
			// 檢查付款方式
			if (!"CASH".equalsIgnoreCase(order.getPaymentMethod())) {
				return new BasicRes(ReplyMessage.PAY_PAYMENT_METHOD_ERROR.getCode(),
						ReplyMessage.PAY_PAYMENT_METHOD_ERROR.getMessage());
			}

			int result = ordersDao.updateCashPayOnSite(req.getId(), req.getOrderDateId(), PayStatus.PAID.name());
			if (result == 0) {
				return new BasicRes(ReplyMessage.UPDATE_PAY_STATUS_ERROR.getCode(),
						ReplyMessage.UPDATE_PAY_STATUS_ERROR.getMessage());
			}
			// 點數扣除邏輯方法
			processMemberLoyalty(order);

			return new BasicRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
		} catch (RuntimeException e) {
			// // 關鍵】因為我們想 return 包裝好的 JSON，所以必須手動標記回滾
			// TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			// // 回傳友善訊息給前端
			// return new BasicRes(500, "操作失敗：" + e.getMessage());

			throw new RuntimeException("現場付款失敗：" + e.getMessage());
		} catch (Exception e) {
			log.error("非預期錯誤: ", e);
			throw new RuntimeException("系統錯誤，請洽工程人員" + e.getMessage());
		}
	}

	/* 會員次數邏輯 */
	private void processMemberLoyalty(Orders order) {
		// 不是會員
		if (order.getMemberId() <= 1)
			return;
		// 取得會員資料
		Members member = membersDao.findById(order.getMemberId());
		if (member == null) {
			// 拋出異常，觸發外層 Transactional 回滾
			throw new RuntimeException("會員資料異常，ID: " + order.getMemberId());
		}
		// 取得該會員國家優惠劵開啟需要的次數門檻
		Discount discount = discountDao.findByRegionsId(member.getRegionsId());
		if (discount == null) {
			throw new RuntimeException("找不到該分店所在國家的折扣額度設定");
		}
		int count = discount.getCount();
		// 如果有使用優惠劵，須把它關閉、次數歸0
		if (order.isUseDiscount()) {
			int updated = membersDao.useDiscount(member.getId());
			if (updated == 0) { // 避免客人有兩筆以上同時請求，可能原因: 狂點按鈕，網路延遲
				throw new RuntimeException("優惠券核銷失敗");
			}
		} else {
			// 加次數(如果次數 < 9 或 > 9 會成功，否則回傳 0)
			int addResult = membersDao.addPoint(member.getId(), count);
			if (addResult == 0) {
				// 代表會員次數加上這筆訂單會剛好來到10次，那就要把優惠劵打開
				int couponResult = membersDao.reachFullPointsAndGiveCoupon(member.getId(), count);
				if (couponResult == 0) {
					throw new RuntimeException("會員點數更新失敗");
				}
			}
		}
	}

	/* POS機用結帳 */
	@Transactional(rollbackFor = Exception.class)
	public BasicRes payForPOS(PayForPOSReq req, HttpSession httpSession) {
		// 抓員工資訊
		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
		if (staff == null) { // 只有管理者才能查詢
			return new GetAllOrdersRes(ReplyMessage.PERMISSION_DENIED.getCode(),
					ReplyMessage.PERMISSION_DENIED.getMessage());
		}
		try {
			CreateOrdersRes createOrdersRes = createOrders(req, httpSession);
			if (createOrdersRes == null) {
				return new BasicRes(500, "建立訂單失敗");
			}
			if (createOrdersRes.getCode() != 200) {
				return new BasicRes(createOrdersRes.getCode(), createOrdersRes.getMessage());
			}
			PayReq payReq = new PayReq();
			payReq.setId(createOrdersRes.getId());
			payReq.setOrderDateId(createOrdersRes.getOrderDateId());
			payReq.setPaymentMethod(req.getPaymentMethod());
			payReq.setTransactionId(req.getTransactionId());
			payReq.setTotalAmount(createOrdersRes.getTotalAmount());
			return pay(payReq, httpSession);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			log.error("非預期錯誤: ", e);
			throw new RuntimeException("系統錯誤，請洽工程人員" + e.getMessage());
		}
	}

	/* 取消訂單 */
	@Transactional(rollbackFor = Exception.class)
	public BasicRes cancelOrder(UpdateOrdersStatusReq req, HttpSession httpSession) {
		// 抓會員資訊(因為會員登入那邊存的是res，所以會多一層)
		MembersRes membersRes = (MembersRes) httpSession.getAttribute(MembersController.ATTRIBUTE_KEY);
		Members member = (membersRes != null) ? membersRes.getMembers() : null;

		Orders order = ordersDao.getOrderByOrderDateIdAndId(req.getOrderDateId(), req.getId());
		// 找不到該筆訂單
		if (order == null) { // 找不到該筆訂單
			return new BasicRes(ReplyMessage.ORDER_NUMBER_NOT_FOUND.getCode(), //
					ReplyMessage.ORDER_NUMBER_NOT_FOUND.getMessage());
		}
		if (member == null) { // 找不到會員
			return new BasicRes(ReplyMessage.MEMBER_NOT_FOUND.getCode(), //
					ReplyMessage.MEMBER_NOT_FOUND.getMessage());
		}
		if (member.getId() != order.getMemberId()) { // 如果與該訂單的會員ID不相符
			return new BasicRes(ReplyMessage.MEMBER_ERROR.getCode(), ReplyMessage.MEMBER_ERROR.getMessage());
		}

		try {

			// 取得該訂單的付款狀態(如果取得是null，後面判斷會出錯，所以轉成"")
			String oldOrdersStatus = (order.getOrdersStatus() != null) ? order.getOrdersStatus().name() : "";
			// 取得該訂單的狀態(如果取得是null，後面判斷會出錯，所以轉成"")
			String oldPayStatus = (order.getPayStatus() != null) ? order.getPayStatus().name() : "";
			String targetStatus = req.getOrdersStatus(); // 目標狀態(之後要更新的狀態)
			// 判斷目標狀態是否為取消
			if (!OrdersStatus.CANCELLED.name().equalsIgnoreCase(targetStatus)) {
				return new BasicRes(ReplyMessage.ORDERS_STATUS_ERROR.getCode(), //
						ReplyMessage.ORDERS_STATUS_ERROR.getMessage());
			}
			// 防呆：只有 PayStatus == UNPAID && OrdersStatus == PREPARING 才能取消
			if (!OrdersStatus.PREPARING.name().equalsIgnoreCase(oldOrdersStatus) || //
					!PayStatus.UNPAID.name().equalsIgnoreCase(oldPayStatus)) {
				return new BasicRes(ReplyMessage.ORDERS_STATUS_ERROR.getCode(), //
						ReplyMessage.ORDERS_STATUS_ERROR.getMessage());
			}

			// 執行訂單狀態更新
			int result = ordersDao.updateOrderStatus(targetStatus, req.getId(), req.getOrderDateId());
			if (result == 0) { // 判斷是否成功
				throw new RuntimeException("取消訂定單失敗");
			}
			if (order.getMemberId() == 1) { // 遊客不用繼續走下去
				return new BasicRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
			}
			// 取得該會員國家優惠劵開啟需要的次數門檻
			Discount discount = discountDao.findByRegionsId(member.getRegionsId());
			if (discount == null) {
				throw new RuntimeException("找不到該分店所在國家的折扣額度設定");
			}
			int count = discount.getCount();
			if (order.isUseDiscount()) {
				membersDao.restoreCouponAndPoints(order.getMemberId(), count);
			} else {
				membersDao.smartReducePoint(order.getMemberId(), count);
			}
			return new BasicRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());

		} catch (Exception e) {
			throw new RuntimeException("取消流程發生錯誤: " + e.getMessage());
		}
	}

	/* 報電話號碼查詢今天的訂單 */
	public GetAllOrdersRes getOrderByPhone(String phone, HttpSession httpSession) {
		// 抓員工資訊
		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
		if (staff == null) { // 只有管理者才能查詢
			return new GetAllOrdersRes(ReplyMessage.PERMISSION_DENIED.getCode(),
					ReplyMessage.PERMISSION_DENIED.getMessage());
		}
		// 取的今天的日期字串，參考成立訂單
		String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		// 取的資料根據電話號碼跟今天日期 DAO 取得扁平化的資料
		List<Object[]> rawData = ordersDao.getOrdersByPhone(todayStr, phone);
		// 用 Map 來群組化，Key 是 "日期+ID"，Value 是訂單 VO
		// LinkedHashMap 是為了「照順序排」，讓最新下單的排在最前面
		Map<String, GetOrdersVo> orderMap = new LinkedHashMap<>();
		for (Object[] row : rawData) {
			String orderKey = row[1].toString() + row[0].toString(); // 日期 + ID

			GetOrdersVo vo = orderMap.computeIfAbsent(orderKey, k -> {
				// 建立一個新的訂單物件
				GetOrdersVo newVo = new GetOrdersVo();
				newVo.setId(row[0] != null ? row[0].toString() : ""); // o.id
				newVo.setOrderDateId(row[1] != null ? row[1].toString() : ""); // o.order_date_id
				newVo.setGlobalAreaId(row[2] != null ? ((Number) row[2]).intValue() : 0); // o.global_area_id
				newVo.setTotalAmount(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO); // o.total_amount
				newVo.setOrdersStatus(row[4] != null ? row[4].toString() : "UNKNOWN"); // o.status
				newVo.setPayStatus(row[5] != null ? row[5].toString() : "UNKNOWN");// o.status
				// 大部分的 JDBC 驅動（如 MySQL Connector/J）在處理資料庫的 DATETIME 或 TIMESTAMP 欄位時，
				// 回傳的 Java 物件實際上是 java.sql.Timestamp
				// 必須先轉成 Timestamp，再呼叫它內建的轉換方法 .toLocalDateTime()
				newVo.setCompletedAt(row[6] != null ? (LocalDateTime) row[6] : null);
				newVo.setGetOrdersDetailVoList(new ArrayList<>());
				return newVo;
			});

			// 建立明細並塞入該訂單的 List
			GetOrdersDetailVo detail = new GetOrdersDetailVo();
			// 如果是寫 Integer ，DB 回傳是：BigInteger/Long，會直接噴 ClassCastException
			detail.setQuantity(row[7] != null ? ((Number) row[7]).intValue() : 0);
			detail.setPrice(row[8] != null ? (BigDecimal) row[8] : BigDecimal.ZERO);
			detail.setGift(row[9] != null && ((Number) row[9]).intValue() == 1); // 回傳0或1，在 Java 是數字，透過比較運算產生 true/false
			detail.setDiscountNote(row[10] != null ? row[10].toString() : "");
			detail.setName(row[11] != null ? row[11].toString() : "未知商品"); // 產品名稱已經在 SQL 抓好了

			vo.getGetOrdersDetailVoList().add(detail);
		}
		// 把 Map 的值轉換成 List
		List<GetOrdersVo> result = new ArrayList<>(orderMap.values());

		return new GetAllOrdersRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(), result);
	}

	/* 更新訂單狀態(待取餐/已取餐) */
	public BasicRes UpdateOrdersStatus(UpdateOrdersStatusReq req, HttpSession httpSession) {
		// 抓員工資訊
		Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
		if (staff == null) { // 只有管理者才能查詢
			return new BasicRes(ReplyMessage.PERMISSION_DENIED.getCode(), ReplyMessage.PERMISSION_DENIED.getMessage());
		}

		// 取得該筆訂單資訊
		Orders order = ordersDao.getOrderByOrderDateIdAndId(req.getOrderDateId(), req.getId());
		if (order == null) {
			return new BasicRes(ReplyMessage.ORDER_NOT_FOUND.getCode(), //
					ReplyMessage.ORDER_NOT_FOUND.getMessage());
		}

		// 如果訂單狀態是已取餐，那就是狀態錯誤
		if (OrdersStatus.PICKED_UP.name().equalsIgnoreCase(order.getOrdersStatus().name())) {
			return new BasicRes(ReplyMessage.ORDERS_STATUS_ERROR.getCode(), //
					ReplyMessage.ORDERS_STATUS_ERROR.getMessage());
		}

		int result = 0;
		// 訂單狀態更新(用於製餐中 -> 待取餐)
		if (OrdersStatus.READY.name().equalsIgnoreCase(req.getOrdersStatus())) {
			result = ordersDao.updateOrderStatusForReady(req.getOrdersStatus(), req.getId(), req.getOrderDateId());

		} else if (OrdersStatus.PICKED_UP.name().equalsIgnoreCase(req.getOrdersStatus())) {
			// 訂單狀態更新(用於待取餐 -> 已取餐)
			result = ordersDao.updateOrderStatusForPickedUp(req.getOrdersStatus(), req.getId(), req.getOrderDateId());

		}
		if (result == 0) {
			return new BasicRes(ReplyMessage.ORDERS_STATUS_ERROR.getCode(), //
					ReplyMessage.ORDERS_STATUS_ERROR.getMessage());
		}

		return new BasicRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
	}

	// /* 申請退款 */
	// @Transactional(rollbackFor = Exception.class)
	// public BasicRes applyForRefund(RefundedReq req, HttpSession httpSession) {
	// // 抓員工資訊
	// Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
	// // 抓會員資訊(因為會員登入那邊存的是res，所以會多一層)
	// MembersRes membersRes = (MembersRes)
	// httpSession.getAttribute(MembersController.ATTRIBUTE_KEY);
	// Members member = (membersRes != null) ? membersRes.getMembers() : null;
	// Orders order = ordersDao.getOrderByOrderDateIdAndId(req.getOrderDateId(),
	// req.getId());
	// // 防呆：只有 PAID(已付款) 才能申請退款
	// if (!PayStatus.PAID.name().equalsIgnoreCase(order.getPayStatus().name())) {
	// return new BasicRes(ReplyMessage.ORDERS_STATUS_ERROR.getCode(),
	// "僅完成之訂單可申請退款");
	// }
	// // 訂單狀態是已取消(退款或取消)，無法申請退款
	// if
	// (OrdersStatus.CANCELLED.name().equalsIgnoreCase(order.getOrdersStatus().name()))
	// {
	// return new BasicRes(ReplyMessage.ORDERS_STATUS_ERROR.getCode(), //
	// ReplyMessage.ORDERS_STATUS_ERROR.getMessage());
	// }
	// // 判斷是員工現場幫客人處理退款，還是客人線上申請退款
	// if (staff != null) { // 員工現場幫客人處理退款
	// return executeFullRefund(req, order);
	// } else if (staff == null && member != null) { // 客人線上申請退款
	// if (member.getId() != order.getMemberId()) { // 如果與該訂單的會員ID不相符
	// return new BasicRes(ReplyMessage.MEMBER_ERROR.getCode(),
	// ReplyMessage.MEMBER_ERROR.getMessage());
	// }
	// // 更新為「退款處理中」，等待店長審核
	// ordersDao.updateOrderStatus("REFUND_PROCESSING", req.getId(),
	// req.getOrderDateId());
	// // TODO: 發送 WebSocket 或推播通知給店長
	// return new BasicRes(ReplyMessage.SUCCESS.getCode(), "退款申請已提交，請等待店長審核");
	// } else { // 遊客操作
	// throw new RuntimeException("請先登入或請找員工處理");
	// }
	// }
	//
	// // 退款實際動作: 狀態更新、點餐次數收回、優惠劵還原
	// @Transactional(rollbackFor = Exception.class)
	// private BasicRes executeFullRefund(RefundedReq req, Orders order) {
	// try {
	// // 執行訂單狀態更新
	// int result = ordersDao.updateOrderStatusAndPayStatus(req.getOrdersStatus(),
	// req.getPayStatus(), //
	// req.getId(), req.getOrderDateId());
	// // 判斷是否成功
	// if (result > 0) {
	// // 如果是會員，次數扣回
	// if (order.getMemberId() > 1) {
	// if (order.isUseDiscount()) {
	// // 【情況 A】：當初有使用優惠券 -> 還原券並將次數設回 10
	// membersDao.restoreCouponAndPoints(order.getMemberId());
	// } else {
	// // 【情況 B】：當初沒用優惠券 -> 正常扣回這單加的 1 次數
	// int pointResult = membersDao.smartReducePoint(order.getMemberId());
	// if (pointResult == 0) {
	// log.warn("退款扣點異常 - 會員ID: {}, 訂單ID: {}, 原因: 點數不足(0)", order.getMemberId(),
	// order.getId());
	// }
	// }
	// }
	// return new BasicRes(ReplyMessage.SUCCESS.getCode(),
	// ReplyMessage.SUCCESS.getMessage());
	// }
	// throw new RuntimeException("更新訂單狀態失敗");
	// } catch (Exception e) {
	// throw new RuntimeException("退款流程發生錯誤: " + e.getMessage());
	// }
	// }
	//
	// /* 店長審核退款 */
	// @Transactional(rollbackFor = Exception.class)
	// public BasicRes auditRefund(RefundedReq req, HttpSession httpSession) {
	// Staff staff = (Staff) httpSession.getAttribute(StaffController.SESSION_KEY);
	// // 判斷有沒有登入
	// if (staff == null) {
	// return new BasicRes(ReplyMessage.NOT_LOGIN.getCode(), //
	// ReplyMessage.NOT_LOGIN.getMessage());
	// }
	// // 判斷是不是店長
	// if (staff.getRole() != StaffRole.REGION_MANAGER) { // 如果身分不是店長
	// return new BasicRes(ReplyMessage.PERMISSION_DENIED.getCode(), //
	// ReplyMessage.PERMISSION_DENIED.getMessage());
	// }
	// Orders order = ordersDao.getOrderByOrderDateIdAndId(req.getOrderDateId(),
	// req.getId());
	// if (order == null) {
	// return new BasicRes(ReplyMessage.ORDER_NUMBER_NOT_FOUND.getCode(), //
	// ReplyMessage.ORDER_NUMBER_NOT_FOUND.getMessage());
	// }
	//
	// try {
	// if (PayStatus.REFUNDED.name().equalsIgnoreCase(req.getPayStatus())) {
	// return executeFullRefund(req, order);
	// } else { // 退款被駁回
	// ordersDao.updateOrderStatus("COMPLETED", req.getId(), req.getOrderDateId());
	// return new BasicRes(ReplyMessage.SUCCESS.getCode(),
	// ReplyMessage.SUCCESS.getMessage());
	// }
	// } catch (Exception e) {
	// // 拋出 RuntimeException 觸發 Transactional Rollback
	// throw new RuntimeException("審核執行失敗：" + e.getMessage());
	// }
	// }

}