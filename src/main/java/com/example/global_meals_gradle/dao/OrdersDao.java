package com.example.global_meals_gradle.dao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.global_meals_gradle.entity.Orders;
import com.example.global_meals_gradle.entity.OrdersId;
import com.example.global_meals_gradle.vo.GetOrdersVo;

@Repository
public interface OrdersDao extends JpaRepository<Orders, OrdersId> {

	/* 新增訂單 */
	@Modifying
	@Transactional
	@Query(value = "INSERT INTO orders (id, order_date_id, order_cart_id, global_area_id, member_id, phone, "
			+ " subtotal_before_tax, tax_amount, total_amount, total_cost, orders_status, pay_status, is_use_discount) "
			+ "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)", nativeQuery = true)
	public void insert(String id, String orderDateId, int orderCartId, int globalAreaId, int memberId, String phone, //
			BigDecimal subtotalBeforeTax, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal totalCost,//
			String ordersStatus, String payStatus, boolean useDiscount);

	/* 根據 orderDateId 查詢特定訂單 */
	// ORDER BY id (排序)(字串排序需長度樣(補零)) DESC (倒序) LIMIT 1 (限制筆數) FOR UPDATE:
	// 查詢到的這筆資料會被鎖住
	// Optional: 如果當天還沒有人下單（第一筆），它會回傳 Optional.empty()，你的 Service 就可以判斷 isPresent()
	// 來給出第一個號碼 0001。
	@Query(value = "SELECT * FROM orders WHERE order_date_id = ?1 ORDER BY id DESC LIMIT 1 FOR UPDATE", nativeQuery = true)
	public Optional<Orders> getOrderByOrderDateId(String orderDateId);

	/* 選擇現金付款新增(更新)的資料(付款方式) */
	@Modifying
	@Transactional
	@Query(value = "UPDATE orders SET payment_method = ?3, pay_status = ?4 WHERE id = ?1 " //
			+ " AND order_date_id = ?2 AND pay_status = 'UNPAID'", nativeQuery = true)
	public int updatePaymentMethod(String id, String orderDateId, String paymentMethod, String payStatus);

	/* 現場現金付款完成新增(更新)的資料(付款狀態) */
	@Modifying
	@Transactional
	@Query(value = "UPDATE orders SET pay_status = ?3 WHERE id = ?1 "
			+ " AND order_date_id = ?2 AND pay_status = 'UNPAID'", nativeQuery = true)
	public int updateCashPayOnSite(String id, String orderDateId, String payStatus);

	/* 付款完成新增(更新)的資料(付款方式、交易號碼、狀態) */
	@Modifying
	@Transactional
	@Query(value = "UPDATE orders SET payment_method = ?3, transaction_id = ?4, pay_status = ?5 WHERE id = ?1 "
			+ " AND order_date_id = ?2 AND pay_status = 'UNPAID'", nativeQuery = true)
	public int updatePay(String id, String orderDateId, String paymentMethod, String transactionId, String payStatus);

	/* 取的該會員的今天訂單 */
	@Query(value = "SELECT id, order_date_id, orders_status FROM orders  " //
			+ "WHERE order_date_id = ?1 AND member_id = ?2 ", nativeQuery = true)
	public List<Object[]> GetOrdersUncomplete(String orderDateId, int memberId);

	/* 根據電話號碼查詢今天的訂單 */
	@Query(value = "SELECT o.id, o.order_date_id, o.global_area_id, o.total_amount, "
			+ "o.orders_status, o.pay_status, o.completed_at," + "d.quantity, d.price, d.is_gift, d.discount_note, "
			+ "p.name as product_name " + "FROM orders o "
			+ "LEFT JOIN order_cart_details d ON o.order_cart_id = d.order_cart_id "
			+ "LEFT JOIN products p ON d.product_id = p.id " + "WHERE o.order_date_id = ?1 AND o.phone = ?2 "
			+ "ORDER BY o.order_date_id DESC, o.id DESC", nativeQuery = true)
	public List<Object[]> getOrdersByPhone(String orderDateId, String phone);

	/* 查詢該分店今天的所有訂單 */
	@Query(value = "SELECT o.id, o.order_date_id, o.global_area_id, o.total_amount, "
			+ "o.orders_status, o.pay_status, o.completed_at," + "d.quantity, d.price, d.is_gift, d.discount_note, "
			+ "p.name as product_name " + "FROM orders o "
			+ "LEFT JOIN order_cart_details d ON o.order_cart_id = d.order_cart_id "
			+ "LEFT JOIN products p ON d.product_id = p.id " + "WHERE o.order_date_id = ?1 AND o.global_area_id = ?2 "
			+ "ORDER BY o.order_date_id DESC, o.id DESC", nativeQuery = true)
	public List<Object[]> getTodayAllOrders(String orderDateId, int globalAreaId);

	/* 依據訂單編號查詢該筆訂單 */
	@Query(value = "SELECT * FROM orders WHERE order_date_id = ?1 AND id = ?2", nativeQuery = true)
	public Orders getOrderByOrderDateIdAndId(String orderDateId, String id);

	/* 查詢該會員的訂單紀錄 */
	@Query(value = "SELECT * FROM orders WHERE member_id = ?1", nativeQuery = true)
	public List<GetOrdersVo> getOrderByMemberId(int memberId);

	/* 查詢該會員的訂單紀錄 */
	@Query(value = "SELECT o.id, o.order_date_id, o.global_area_id, o.total_amount, "
			+ "o.orders_status, o.pay_status, o.completed_at," + "d.quantity, d.price, d.is_gift, d.discount_note, "
			+ "p.name as product_name " + "FROM orders o "
			+ "LEFT JOIN order_cart_details d ON o.order_cart_id = d.order_cart_id "
			+ "LEFT JOIN products p ON d.product_id = p.id " + "WHERE o.member_id = ?1 "
			+ "ORDER BY o.order_date_id DESC, o.id DESC", nativeQuery = true)
	public List<Object[]> getFullOrderHistory(int memberId);

	/* 訂單狀態更新(用於製餐中 -> 待取餐) */
	@Modifying
	@Transactional
	@Query(value = "UPDATE orders SET orders_status = :ordersStatus "
			+ "WHERE id = :id AND order_date_id = :orderDateId And orders_status = 'PREPARING'", nativeQuery = true)
	public int updateOrderStatusForReady(@Param("ordersStatus") String ordersStatus, //
			@Param("id") String id, @Param("orderDateId") String orderDateId);

	/* 訂單狀態更新(用於待取餐 -> 已取餐) */
	@Modifying
	@Transactional
	@Query(value = "UPDATE orders SET orders_status = :ordersStatus "
			+ "WHERE id = :id AND order_date_id = :orderDateId And orders_status = 'READY'", nativeQuery = true)
	public int updateOrderStatusForPickedUp(@Param("ordersStatus") String ordersStatus, //
			@Param("id") String id, @Param("orderDateId") String orderDateId);

	/* 訂單狀態更新(用於取消訂單) */
	@Modifying
	@Transactional
	@Query(value = "UPDATE orders SET orders_status = :ordersStatus "
			+ "WHERE id = :id AND order_date_id = :orderDateId And pay_status = 'UNPAID'", nativeQuery = true)
	public int updateOrderStatus(@Param("ordersStatus") String ordersStatus, //
			@Param("id") String id, @Param("orderDateId") String orderDateId);

	/* 訂單狀態更新(用於退款訂單) */
	@Modifying
	@Transactional
	@Query(value = "UPDATE orders SET orders_status = :ordersStatus, pay_status = :payStatus "
			+ "WHERE id = :id AND order_date_id = :orderDateId And pay_status = 'PAID'", nativeQuery = true)
	public int updateOrderStatusAndPayStatus(@Param("ordersStatus") String ordersStatus, //
			@Param("payStatus") String payStatus, //
			@Param("id") String id, @Param("orderDateId") String orderDateId);

	// 一次取得該分店、該月份、已取餐訂單的總營業額與總成本
	@Query(value = "SELECT SUM(total_amount) AS totalAmount, SUM(total_cost) AS totalCost " //
	        + "FROM orders " //
	        + "WHERE global_area_id = ?1 " //
	        + "AND orders_status = 'PICKED_UP' " //
	        + "AND completed_at BETWEEN ?2 AND ?3", nativeQuery = true)
	public Object[] findRevenueAndCostByGlobalAreaId(int branchId, LocalDateTime start, LocalDateTime end);

	// 查詢某分店的營業額(一個區間)(for 店長)
	@Query(value = "SELECT g.branch AS branchName, r.country AS regionsName, "
	        + "SUM(o.total_amount) AS totalAmount, " 
	        + "SUM(o.total_cost) AS totalCost "
	        + "FROM orders o "
	        + "JOIN global_area g ON o.global_area_id = g.id "
	        + "JOIN regions r ON g.regions_id = r.id "
	        + "WHERE o.global_area_id = ?1 "
	        + "AND o.orders_status = 'PICKED_UP' " // 狀態過濾
	        + "AND o.completed_at BETWEEN ?2 AND ?3 " // 改用完成時間
	        + "GROUP BY g.id, g.branch, r.country", nativeQuery = true)
	public List<Object[]> findSingleBranchRevenue(Integer branchId, LocalDateTime start, LocalDateTime end);

	// 查詢特定國家內，每一間分店的營業額(一個區間)(for 老闆)
	@Query(value = "SELECT g.branch AS branchName, r.country AS regionsName, "
	        + "SUM(o.total_amount) AS totalAmount, " 
	        + "SUM(o.total_cost) AS totalCost "
	        + "FROM orders o "
	        + "JOIN global_area g ON o.global_area_id = g.id "
	        + "JOIN regions r ON g.regions_id = r.id "
	        + "WHERE r.id = ?1 "
	        + "AND o.orders_status = 'PICKED_UP' " // 狀態過濾
	        + "AND o.completed_at BETWEEN ?2 AND ?3 " // 改用完成時間
	        + "GROUP BY g.id, g.branch, r.country", nativeQuery = true)
	public List<Object[]> findRevenueByRegionGroupedByBranch(Integer regionsId, //
			LocalDateTime start, LocalDateTime end);

	// 查詢每一間分店的營業額(一個區間)(for 老闆)
	@Query(value = "SELECT g.branch AS branchName, r.country AS regionsName, "
	        + "SUM(o.total_amount) AS totalAmount, " 
	        + "SUM(o.total_cost) AS totalCost "
	        + "FROM orders o "
	        + "JOIN global_area g ON o.global_area_id = g.id "
	        + "JOIN regions r ON g.regions_id = r.id "
	        + "WHERE o.orders_status = 'PICKED_UP' " // 狀態過濾
	        + "AND o.completed_at BETWEEN ?1 AND ?2 " // 改用完成時間
	        + "GROUP BY g.id, g.branch, r.country", nativeQuery = true)
	public List<Object[]> findRevenue(LocalDateTime start, LocalDateTime end);

	/**
	 * 檢查這個購物車 ID 是否已經被結帳（存在於訂單表中）
	 * 
	 * 條件說明： order_cart_id = :orderCartId → 尋找這台購物車 SELECT EXISTS 會回傳 boolean（1 或
	 * 0），效能最好
	 */
	@Query("SELECT COUNT(o) > 0 FROM Orders o WHERE o.orderCartId = :orderCartId")
	boolean existsByOrderCartId(@Param("orderCartId") int orderCartId);

	// =====================================================================
	// 功能A：分店長用 - 查某年某月「指定分店」所有商品銷售量
	// 說明：
	// AND o.global_area_id = :globalAreaId → 只算該分店的訂單
	// =====================================================================
	@Query(value = "SELECT p.name AS productName, SUM(d.quantity) AS totalQuantity " + "FROM orders o "
			+ "LEFT JOIN order_cart_details d ON o.order_cart_id = d.order_cart_id "
			+ "LEFT JOIN products p ON d.product_id = p.id " + "WHERE o.order_date_id LIKE :yearMonth "
			+ "AND o.global_area_id = :globalAreaId " + "AND o.orders_status = 'PICKED_UP' " + "AND d.is_gift = 0 "
			+ "GROUP BY d.product_id, p.name " + "ORDER BY totalQuantity DESC", nativeQuery = true)
	List<Object[]> getMonthlySalesByBranch(@Param("yearMonth") String yearMonth,
			@Param("globalAreaId") int globalAreaId);

	// =====================================================================
	// 功能B：老闆用 - 查某年某月「指定國家」所有分店銷售前5名商品
	// =====================================================================
	@Query(value = "SELECT p.name AS productName, SUM(d.quantity) AS totalQuantity " + "FROM orders o "
			+ "LEFT JOIN order_cart_details d ON o.order_cart_id = d.order_cart_id "
			+ "LEFT JOIN products p ON d.product_id = p.id " + "LEFT JOIN global_area ga ON o.global_area_id = ga.id "
			+ "LEFT JOIN regions r ON ga.regions_id = r.id " + "WHERE o.order_date_id LIKE :yearMonth "
			+ "AND r.id = :regionId " + "AND o.orders_status = 'PICKED_UP' " + "AND d.is_gift = 0 "
			+ "GROUP BY d.product_id, p.name " + "ORDER BY totalQuantity DESC " + "LIMIT 5", nativeQuery = true)
	List<Object[]> getTop5MonthlySalesByRegion(@Param("yearMonth") String yearMonth, @Param("regionId") int regionId);
	
	
	
	/* 新增訂單(測試) */
	@Modifying
	@Transactional
	@Query(value = "INSERT INTO orders (id, order_date_id, order_cart_id, global_area_id, member_id, phone, " //
			+ " subtotal_before_tax, tax_amount, total_amount, total_cost, payment_method, " //
			+ "orders_status, pay_status, completed_at, is_use_discount) " //
			+ "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)", nativeQuery = true)
	public void insertTest(String id, String orderDateId, int orderCartId, int globalAreaId, int memberId, String phone, //
			BigDecimal subtotalBeforeTax, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal totalCost, //
			String paymentMethod, String ordersStatus, String payStatus, LocalDateTime completedAt, boolean useDiscount);

}