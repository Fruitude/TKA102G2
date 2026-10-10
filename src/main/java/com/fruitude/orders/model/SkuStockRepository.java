package com.fruitude.orders.model;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.fruitude.product.model.ProductSku;

/**
 * 下單時扣庫存用的資料存取：鎖定規格的資料列，以及「庫存夠才扣」的原子更新。
 * 這些方法都必須在交易裡呼叫（OrdersService.placeOrder 的交易）。
 */
public interface SkuStockRepository extends Repository<ProductSku, Integer> {

	// 鎖住這些規格的資料列（FOR UPDATE），一直到交易結束（commit 或 rollback）才放開。
	// 其他交易如果也要鎖定讀取這些列的庫存（前台查庫存用 FOR SHARE），會等到這裡的交易結束才能讀。
	// 一定要依 sku_id 由小到大鎖，兩筆訂單才不會因為鎖定順序相反而互相等待（死結）
	@Query(value = "SELECT sku_id FROM product_sku WHERE sku_id IN (:skuIds) ORDER BY sku_id FOR UPDATE", nativeQuery = true)
	List<Integer> lockSkus(@Param("skuIds") Collection<Integer> skuIds);

	// 原子保留庫存：下單只把 outbound_qty（待出貨）加上訂購數量，不動 stock（出貨時才扣 stock）。
	// 可售量 = stock + inbound_qty − outbound_qty；下單後的可售量不能低於「最低允許值」：
	// 狀態 1、3 為 0（有貨才賣），狀態 2（缺貨）為 −max_backorder_qty（可以預購，但不能超過預購額度）。
	// 檢查與增加寫在同一個 UPDATE 的 WHERE 裡，不會被別的交易插進來；
	// 回傳 0 代表庫存或預購額度不夠（沒有任何變動），呼叫端要丟例外讓整筆訂單 rollback
	@Modifying
	@Query(value = "UPDATE product_sku SET outbound_qty = COALESCE(outbound_qty, 0) + :qty "
			+ "WHERE sku_id = :skuId AND status IN (1,2,3) AND EXISTS (SELECT 1 FROM product p WHERE p.product_id = product_sku.product_id AND p.status = 1) "
			+ "AND COALESCE(stock, 0) + COALESCE(inbound_qty, 0) - COALESCE(outbound_qty, 0) - :qty "
			+ ">= CASE WHEN status = 2 THEN -COALESCE(max_backorder_qty, 0) ELSE 0 END", nativeQuery = true)
	int deductStock(@Param("skuId") Integer skuId, @Param("qty") Integer qty);

	// 出貨：實體庫存與待出貨各減掉出貨數量（不讓欄位變成負數）
	@Modifying
	@Query(value = "UPDATE product_sku SET stock = GREATEST(COALESCE(stock, 0) - :qty, 0), "
			+ "outbound_qty = GREATEST(COALESCE(outbound_qty, 0) - :qty, 0) WHERE sku_id = :skuId", nativeQuery = true)
	int shipStock(@Param("skuId") Integer skuId, @Param("qty") Integer qty);

	// 取消（尚未出貨）：只把待出貨還回去，stock 本來就沒扣
	@Modifying
	@Query(value = "UPDATE product_sku SET outbound_qty = GREATEST(COALESCE(outbound_qty, 0) - :qty, 0) WHERE sku_id = :skuId", nativeQuery = true)
	int releaseOutbound(@Param("skuId") Integer skuId, @Param("qty") Integer qty);

	// 規則 A：上架（1）的可售量低於安全庫存 → 缺貨（2）
	@Modifying
	@Query(value = "UPDATE product_sku SET status = 2, updated_at = CURRENT_TIMESTAMP WHERE sku_id IN (:skuIds) AND status = 1 "
			+ "AND COALESCE(stock,0) + COALESCE(inbound_qty,0) - COALESCE(outbound_qty,0) < COALESCE(safety_stock,0)", nativeQuery = true)
	int markOutOfStock(@Param("skuIds") Collection<Integer> skuIds);
    @Query(value = "SELECT product_id FROM product WHERE product_id IN (SELECT product_id FROM product_sku WHERE sku_id IN (:skuIds)) ORDER BY product_id FOR UPDATE", nativeQuery = true)
    List<Integer> lockProducts(@Param("skuIds") Collection<Integer> skuIds);

    // 規則 B：即將售完（3）可售量 ≤ 0 → 售完（5）；缺貨（2）預購額度用完（可售量 ≤ −額度）也轉為售完（5）
    @Modifying
    @Query(value = "UPDATE product_sku SET status = 5, updated_at = CURRENT_TIMESTAMP WHERE sku_id IN (:skuIds) "
            + "AND COALESCE(stock,0) + COALESCE(inbound_qty,0) - COALESCE(outbound_qty,0) <= "
            + "CASE WHEN status = 2 THEN -COALESCE(max_backorder_qty,0) WHEN status = 3 THEN 0 ELSE -2147483648 END "
            + "AND status IN (2,3)", nativeQuery = true)
    int markSoldOut(@Param("skuIds") Collection<Integer> skuIds);

    @Modifying
    @Query(value = "UPDATE product p SET p.status = 0, p.updated_at = CURRENT_TIMESTAMP WHERE p.product_id IN (SELECT s.product_id FROM product_sku s WHERE s.sku_id IN (:skuIds)) AND p.status = 1 AND NOT EXISTS (SELECT 1 FROM product_sku active WHERE active.product_id = p.product_id AND active.status IN (1,2,3))", nativeQuery = true)
    int closeProductsWithoutListedSkus(@Param("skuIds") Collection<Integer> skuIds);
}
