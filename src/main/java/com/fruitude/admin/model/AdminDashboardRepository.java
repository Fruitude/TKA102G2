package com.fruitude.admin.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.fruitude.product.model.ProductSku;

// 後台首頁統計用，只放唯讀查詢；繼承空的 Repository，不提供 CRUD
// 所有統計共用同一組條件：期間內、排除取消訂單（2、4）、數量 > 0，規則與前台熱銷排行一致
public interface AdminDashboardRepository extends Repository<ProductSku, Integer> {

	String SALES_FILTER = """
		WHERE o.orders_date >= :fromDate AND o.orders_date < :toDate
		  AND o.orders_status NOT IN (2,4) AND d.orders_quantity > 0
		""";

	// 期間內各 SKU 銷售量排行
	@Query(value = """
		SELECT s.sku_id AS skuId, p.product_name AS productName,
		       s.sku_name AS skuName, s.another_name AS anotherName,
		       SUM(d.orders_quantity) AS totalQuantity,
		       SUM(d.orders_quantity * COALESCE(d.unit_price,0)) AS totalAmount
		FROM orders o JOIN orders_detail d ON d.orders_id = o.orders_id
		JOIN product_sku s ON s.sku_id = d.sku_id
		JOIN product p ON p.product_id = s.product_id
		""" + SALES_FILTER + """
		GROUP BY s.sku_id, p.product_name, s.sku_name, s.another_name
		ORDER BY totalQuantity DESC, s.sku_id ASC
		LIMIT :limit
		""", nativeQuery = true)
	List<TopSellingSkuRow> findTopSellingSkus(@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate, @Param("limit") int limit);

	// 期間內每月銷售數量與金額（只回傳有銷售的月份，空月份由 service 補 0）
	@Query(value = """
		SELECT DATE_FORMAT(o.orders_date, '%Y-%m') AS month,
		       SUM(d.orders_quantity) AS totalQuantity,
		       SUM(d.orders_quantity * COALESCE(d.unit_price,0)) AS totalAmount
		FROM orders o JOIN orders_detail d ON d.orders_id = o.orders_id
		""" + SALES_FILTER + """
		GROUP BY DATE_FORMAT(o.orders_date, '%Y-%m')
		ORDER BY month
		""", nativeQuery = true)
	List<MonthlySalesRow> findMonthlySales(@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate);

	// 期間內各供應商銷售金額排行；商品沒有設定供應商時 vendorId 為 null
	@Query(value = """
		SELECT v.vendor_id AS vendorId, v.vendor_name AS vendorName,
		       SUM(d.orders_quantity * COALESCE(d.unit_price,0)) AS totalAmount
		FROM orders o JOIN orders_detail d ON d.orders_id = o.orders_id
		JOIN product_sku s ON s.sku_id = d.sku_id
		JOIN product p ON p.product_id = s.product_id
		LEFT JOIN vendor v ON v.vendor_id = p.vendor_id
		""" + SALES_FILTER + """
		GROUP BY v.vendor_id, v.vendor_name
		ORDER BY totalAmount DESC, v.vendor_id ASC
		LIMIT :limit
		""", nativeQuery = true)
	List<VendorSalesRow> findTopVendorSales(@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate, @Param("limit") int limit);

	// 期間內總銷售金額，用來算圓餅圖的「其他」
	@Query(value = """
		SELECT COALESCE(SUM(d.orders_quantity * COALESCE(d.unit_price,0)), 0)
		FROM orders o JOIN orders_detail d ON d.orders_id = o.orders_id
		""" + SALES_FILTER, nativeQuery = true)
	Number findTotalSalesAmount(@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate);

	interface TopSellingSkuRow {
		Integer getSkuId();
		String getProductName();
		String getSkuName();
		String getAnotherName();
		Number getTotalQuantity();
		Number getTotalAmount();
	}

	interface MonthlySalesRow {
		String getMonth();
		Number getTotalQuantity();
		Number getTotalAmount();
	}

	interface VendorSalesRow {
		Integer getVendorId();
		String getVendorName();
		Number getTotalAmount();
	}
}
