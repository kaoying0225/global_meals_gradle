package com.example.global_meals_gradle.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.global_meals_gradle.entity.BranchInventory;
import com.example.global_meals_gradle.entity.Products;

@Repository
public interface BranchInventoryDao extends JpaRepository<BranchInventory, Integer> {

	// 1-1. 修改庫存 => 分店長直接修改 (覆蓋庫存) => 改成使用 saveAll
	// @Modifying
	// @Transactional
	// @Query(value = "UPDATE branch_inventory " //
	// + " SET stock_quantity = ?3 " //
	// + " WHERE product_id = ?1 AND global_area_id = ?2", nativeQuery = true)
	// int updateStockDirectly(int productId, int globalAreaId, int newStock);

	// 1-2. 修改庫存 => 訂單下單扣庫存
	@Modifying
	@Transactional
	@Query(value = "UPDATE branch_inventory " //
			+ " SET stock_quantity = stock_quantity - :quantityToBuy, " //
			+ " version = version + 1 " //
			+ " WHERE product_id = :productId " //
			+ " AND global_area_id = :globalAreaId " //
			+ " AND stock_quantity >= :quantityToBuy " //
			+ " AND version = :oldVersion", nativeQuery = true)
	public int updateBranchStock( //
			@Param("productId") int productId, //
			@Param("globalAreaId") int globalAreaId, //
			@Param("quantityToBuy") int quantityToBuy, //
			@Param("oldVersion") int oldVersion);

	// 2. 修改售價 => 改成使用 saveAll
	// @Modifying
	// @Transactional
	// @Query(value = "UPDATE branch_inventory SET base_price = ?3 " //
	// + " WHERE product_id = ?1 AND global_area_id = ?2", nativeQuery = true)
	// public int updatePrice(int productId, int globalAreaId, BigDecimal
	// basePrice);

	// 3. 查詢某分店某商品的庫存量 - 給組員 1 使用
	@Query(value = "SELECT * FROM branch_inventory " //
			+ " WHERE product_id = ?1 AND global_area_id = ?2", nativeQuery = true)
	public BranchInventory findByProductIdAndAreaId(int productId, int globalAreaId);

	// 4. 查詢某分店某商品的庫存量 - 給組員 2 使用
	// 但其實 3 跟 4 是一樣的結果只是取用方式不同
	public Optional<BranchInventory> findByProductIdAndGlobalAreaId(int productId, int globalAreaId);

	// 5. 查詢某分店某商品的庫存量 - 查詢分店庫存的使用，以防有誤呼叫的狀況底下
	@Query(value = "SELECT * FROM branch_inventory " //
			+ " WHERE product_id = ?1 AND global_area_id = ?2", nativeQuery = true)
	List<BranchInventory> findByProductIdAndGlobalAreaIdForStaff(int productId, int globalAreaId);

	// 6. 分店菜單列表 (JOIN 兩表)
	// 以庫存表為主，撈出該分店所有「上架」商品
	@Query(value = "SELECT p.id, p.name, p.category_id,p.style_id, p.description, p.food_img, " // 順序 0, 1, 2, 3, 4
		    + " bi.base_price, bi.stock_quantity, bi.is_active " // 順序 5, 6, 7
		    + " FROM branch_inventory AS bi "
		    + " JOIN products AS p ON bi.product_id = p.id "
		    + " WHERE bi.global_area_id = ?1 "
		    + " AND p.is_active = 1 AND p.deleted_at IS NULL", nativeQuery = true)
		public List<Object[]> getMenuByArea(int globalAreaId);
	
	// 分店菜單列表
	@Query("SELECT p, bi.basePrice, bi.stockQuantity, bi.active FROM Products p " +
		       "JOIN BranchInventory bi ON p.id = bi.productId " +
		       "WHERE bi.globalAreaId = ?1 " +
		       "AND p.active = true " +       // 總部必須是上架狀態
		       "AND p.deletedAt IS NULL")     // 總部必須未刪除
	public List<Object[]> getMenuEntitiesByArea(int globalAreaId);

	// 7. 透過分店 ID 找出全部的商品庫存
	public List<BranchInventory> findByGlobalAreaId(int globalAreaId);

	// 8. 透過商品 ID 找出全部相對應的分店庫存
	public List<BranchInventory> findByProductId(int productId);
	
	@Modifying(clearAutomatically = true)
	@Query(value="UPDATE branch_inventory SET is_active = :active WHERE product_id = :productId", nativeQuery = true)
	void updateActiveByProductId(@Param("productId") int productId, @Param("active") boolean active);
	
	@Query(value = "SELECT * FROM branch_inventory  "
		    + " WHERE is_active = 1 AND global_area_id BETWEEN 1 AND 6 ", nativeQuery = true)
	public List<BranchInventory> findAllByActive();
	
	// 專門抓特定分店且啟用的商品
	@Query(value = "SELECT * FROM branch_inventory  "
		    + " WHERE is_active = ?2 AND global_area_id = ?1 ", nativeQuery = true)
	public List<BranchInventory> findByGlobalAreaIdAndIsActive(int globalAreaId, int isActive);
}
