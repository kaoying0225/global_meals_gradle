package com.example.global_meals_gradle.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.global_meals_gradle.constants.OrdersStatus;
import com.example.global_meals_gradle.req.CashPayOnSiteReq;
import com.example.global_meals_gradle.req.CreateOrdersReq;
import com.example.global_meals_gradle.req.PayForPOSReq;
import com.example.global_meals_gradle.req.PayReq;
import com.example.global_meals_gradle.req.UpdateOrdersStatusReq;
import com.example.global_meals_gradle.res.BasicRes;
import com.example.global_meals_gradle.res.CreateOrdersRes;
import com.example.global_meals_gradle.res.GetAllOrdersRes;
import com.example.global_meals_gradle.res.GetAllOrdersUncompleteRes;
import com.example.global_meals_gradle.service.EcpayService;
import com.example.global_meals_gradle.service.LinePayService;
import com.example.global_meals_gradle.service.OrdersService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
@Tag(name = "訂單管理模組", description = "處理訂單查詢、建立、狀態更新及第三方金流串接")
public class OrdersController {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrdersController.class);

	@Autowired
	private OrdersService ordersService;

	@Autowired
	private EcpayService ecpayService;

	@Autowired
	private LinePayService linePayService;

	/* 取的該會員的今天所有訂單 */
	@GetMapping("get_all_today_orders_list")
	@Operation(summary = "取的該會員的今天所有訂單", description = "查詢該會員的所有歷史訂單記錄")
	public GetAllOrdersUncompleteRes getTodayOrdersListByMember(@Parameter(hidden = true) HttpSession httpSession) {
		return ordersService.getTodayOrdersListByMember(httpSession);
	}

	/* 取的該會員的歷史訂單 */
	@GetMapping("get_all_orders_list")
	@Operation(summary = "取得會員歷史訂單", description = "查詢該會員的所有歷史訂單記錄")
	public GetAllOrdersRes getAllOrdersListByMember(@RequestParam("memberId") Integer memberId, //
			@Parameter(hidden = true) HttpSession httpSession) {
		return ordersService.getAllOrders(memberId, httpSession);
	}

	/* 取的該分店今天所有訂單 */
	@GetMapping("get_today_all_orders_list")
	@Operation(summary = "取的該分店今天所有訂單", description = "查詢今天所有訂單記錄")
	public GetAllOrdersRes getTodayAllOrdersListByBranch(@Parameter(hidden = true) HttpSession httpSession) {
		return ordersService.getTodayAllOrders(httpSession);
	}

	/* 更改訂單狀態: 待取餐(店員操作)/已取餐(店員操作)/取消(顧客操作) */
	@PostMapping("orders_status")
	@Operation(summary = "更改訂單狀態", description = "將訂單狀態更新為 READY (餐點完成/待取餐) 或 PICKED_UP (已取餐)")
	public BasicRes UpdateOrdersStatus(@Valid @RequestBody UpdateOrdersStatusReq req, //
			@Parameter(hidden = true) HttpSession httpSession) {
		if (OrdersStatus.CANCELLED.name().equalsIgnoreCase(req.getOrdersStatus())) {
			return ordersService.cancelOrder(req, httpSession);
		} else {
			return ordersService.UpdateOrdersStatus(req, httpSession);
		}
	}

	/* 成立訂單(未結帳) */
	@PostMapping("create_orders")
	@Operation(summary = "建立新訂單", description = "新增一筆未結帳的訂單")
	public CreateOrdersRes createOrders(@Valid @RequestBody CreateOrdersReq req, //
			@Parameter(hidden = true) HttpSession httpSession) {
		return ordersService.createOrders(req, httpSession);
	}

	/* 線上付款確認 */
	@PostMapping("pay")
	@Operation(summary = "線上付款確認", description = "紀錄訂單已線上付款")
	public BasicRes pay(@Valid @RequestBody PayReq req, HttpSession httpSession) {
		return ordersService.pay(req, httpSession);
	}

	/* 現金現場付款成功(線上點餐，臨櫃付款) */
	@PostMapping("cash_confirm")
	@Operation(summary = "現金付款確認", description = "紀錄訂單已使用現金完成付款")
	public BasicRes cashPayOnSite(@Valid @RequestBody CashPayOnSiteReq req, //
			@Parameter(hidden = true) HttpSession httpSession) {
		return ordersService.cashPayOnSite(req, httpSession);
	}

	/* POS成立訂單並結帳成功 */
	@PostMapping("pay_POS")
	@Operation(summary = "POS機結帳", description = "POS成立訂單並結帳成功")
	public BasicRes payForPOS(@Valid @RequestBody PayForPOSReq req, //
			@Parameter(hidden = true) HttpSession httpSession) {
		return ordersService.payForPOS(req, httpSession);
	}

	/* 報電話號碼取餐(今天) */
	@GetMapping("get_order_by_phone")
	@Operation(summary = "手機號碼取餐", description = "根據電話號碼查詢今日待取餐訂單")
	public GetAllOrdersRes getOrderByPhone(@RequestParam("phone") String phone, //
			@Parameter(hidden = true) HttpSession httpSession) {
		return ordersService.getOrderByPhone(phone, httpSession);
	}
	
	// 前端點擊「前往付款」時請求的 API
	@GetMapping("/goPay")
	@Operation(summary = "前往付款頁面", description = "根據選擇的付款方式 (ECPAY/LINEPAY) 轉導至金流平台")
	public String goPay(@RequestParam("orderDateId") String orderDateId, @RequestParam("id") String id, //
			@RequestParam("way") String way) {
		// 判斷付款方式是否為綠界 (ECPAY) 還是LINE Pay
		if ("ECPAY".equalsIgnoreCase(way)) {
			// 執行綠界刷卡
			return ecpayService.getEcpayForm(orderDateId, id);
		} else if ("LINEPAY".equalsIgnoreCase(way)) {
			// LINE Pay 回傳的是一個網址 (例如: https://pay-store.line.me/...)
			String payUrl = linePayService.getLinePayLink(orderDateId, id);
			// 這邊要注意：Controller 若要跳轉，不能只傳回字串，通常要用 redirect 或讓前端處理
			return "redirect:" + payUrl;
		}
		return "Unsupported payment method";
	}

	/* 接收金流公司傳的付款成功通知 */
	@PostMapping("/payment/callback")
	@Operation(summary = "金流回呼通知", description = "接收綠界等第三方金流的付款結果回傳 (內部 API)")
	public String handlePaymentNotify(@RequestParam Map<String, String> params,
			//
			@Parameter(hidden = true) HttpSession httpSession) {
		// 取得金流公司傳回來的結果 (RtnCode 是綠界的標準)
		String rtnCode = params.get("RtnCode");
		// 注意：因為我們在發起支付時，MerchantTradeNo 通常會加隨機碼防止重複 (如: 202604110001T123)
		String merchantTradeNo = params.get("MerchantTradeNo");

		if ("1".equals(rtnCode)) {
			// 拆解編號：這裡要根據你當時「丟出去」的格式來拆
			// 假設前 8 位是日期 (20260411)，接著 4 位是序號 (0001)
			String orderDateId = merchantTradeNo.substring(0, 8);
			// 如果你有加隨機碼，就用 split 拆開，只拿前面的序號
			String id = merchantTradeNo.substring(8).split("T")[0];

			// 取得綠界回傳的原始支付類型 (例如: Credit_Card, LINEPAY, WebATM)
			String ecpayPaymentType = params.get("PaymentType");
			String myPaymentMethod = "UNKNOWN";
			// 轉換成你系統定義的關鍵字
			if (ecpayPaymentType.contains("Credit")) {
				myPaymentMethod = "CREDIT_CARD";
			} else if (ecpayPaymentType.contains("LINEPAY")) {
				myPaymentMethod = "LINE_PAY";
			} else {
				myPaymentMethod = "ONLINE_OTHER"; // 其他線上支付
			}
			// 綠界回傳的欄位名稱通常是 "TradeAmt"(付款金額)
			String tradeAmt = params.get("TradeAmt");
			// 封裝成 PayReq 物件，符合 Service 的參數要求
			PayReq req = new PayReq();
			req.setOrderDateId(orderDateId);
			req.setId(id);
			req.setPaymentMethod(myPaymentMethod); // 付款方式
			req.setTransactionId(params.get("TradeNo")); // 綠界的交易流水號
			if (tradeAmt != null) {
				req.setTotalAmount(new BigDecimal(tradeAmt));
			}
			// 呼叫「收錢結案」的共用 Service
			// 不論現金或刷卡，收完錢都是跑這一支
			BasicRes res = ordersService.pay(req, httpSession);

			if (res.getCode() == 200) {
				return "1|OK"; // 綠界要求：成功必須回傳 1|OK，不然它會間隔發送通知直到 24 小時
			}
		}
		return "0|Fail"; // 告訴金流公司處理失敗
	}

	/**
	 * LINE Pay 支付完成後跳轉回來的網址 (ConfirmUrl)
	 */
	@GetMapping("/linepay/confirm")
	@Operation(summary = "LinePay 付款確認", description = "接收 LinePay 支付完成後的確認導回")
	public String linePayConfirm(@RequestParam("transactionId") String transactionId, // LINE Pay 給的交易序號
			@RequestParam("orderDateId") String orderDateId, // 我們自己傳過去的參數 (透傳)
			@RequestParam("id") String id, //
			@Parameter(hidden = true) HttpSession httpSession) {
		try {
			// 執行確認扣款
			linePayService.confirmPayment(transactionId, orderDateId, id, httpSession);

			// 扣款成功後，將客人導向前端的成功頁面
			return "redirect:https://your-frontend.com/payment-success";
		} catch (Exception e) {
			log.error("LINE Pay確認付款失敗", e);
			// 失敗則導向錯誤頁面
			return "redirect:https://your-frontend.com/payment-fail";
		}
	}

	// /* 更改訂單狀態: 訂單退款或取消 */
	// // 退款會傳的狀態: PayStatus = REFUNDED && OrdersStatus = CANCELLED
	// // 取消會傳的狀態: PayStatus = null && OrdersStatus = CANCELLED
	// @PostMapping("orders_status")
	// @Operation(summary = "更改訂單狀態", description = "將訂單狀態更新為 REFUNDED (退款) 或
	// CANCELLED (取消)")
	// public BasicRes ordersStatus(@Valid @RequestBody RefundedReq req, //
	// @Parameter(hidden = true) HttpSession httpSession) {
	// // 區分退款或取消
	// if(PayStatus.REFUNDED.name().equalsIgnoreCase(req.getPayStatus())) {
	// return ordersService.applyForRefund(req, httpSession);
	// }else
	// if(OrdersStatus.CANCELLED.name().equalsIgnoreCase(req.getOrdersStatus())) {
	// return ordersService.cancelOrder(req, httpSession);
	// }else {
	// return new BasicRes(ReplyMessage.ORDERS_STATUS_ERROR.getCode(), //
	// ReplyMessage.ORDERS_STATUS_ERROR.getMessage());
	// }
	// }

}
