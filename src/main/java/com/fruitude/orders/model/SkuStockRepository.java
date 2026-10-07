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

	// 原子扣庫存：stock 減掉訂購數量、outbound_qty 加上訂購數量，兩個欄位在同一個 UPDATE 一起改。
	// 「庫存夠才扣」的條件（stock >= 數量）寫在 WHERE 裡，檢查與扣除是同一個動作，不會被別的交易插進來；
	// 回傳 0 代表庫存不夠（沒有任何變動），呼叫端要丟例外讓整筆訂單 rollback。
	// 狀態 2（缺貨，備貨時間較長、本來就允許訂購）沒有庫存也能下單：stock 最多扣到 0，不擋
	@Modifying
	@Query(value = "UPDATE product_sku SET "
			+ "stock = CASE WHEN status = 2 THEN GREATEST(COALESCE(stock, 0) - :qty, 0) ELSE stock - :qty END, "
			+ "outbound_qty = COALESCE(outbound_qty, 0) + :qty "
			+ "WHERE sku_id = :skuId AND (status = 2 OR stock >= :qty)", nativeQuery = true)
	int deductStock(@Param("skuId") Integer skuId, @Param("qty") Integer qty);
}
