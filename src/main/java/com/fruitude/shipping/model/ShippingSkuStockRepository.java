package com.fruitude.shipping.model;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fruitude.product.model.ProductSku;

import jakarta.persistence.LockModeType;

// 出貨對商品規格（product_sku）的查詢與庫存更新，和商品、訂單、採購模組的 repository 分開，避免互相修改
public interface ShippingSkuStockRepository extends JpaRepository<ProductSku,Integer>{

	// 出貨前鎖住要扣庫存的規格（FOR UPDATE）並取出，一直到交易結束才放開
	// 依規格編號由小到大鎖，和訂單模組下單、改狀態的鎖定順序相同，才不會互相等待而卡死
	// 必須在交易裡呼叫（ShippingService 的 shipOrders）
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from ProductSku s where s.skuId in :skuIds order by s.skuId asc")
	List<ProductSku> lockSkus(@Param("skuIds") Collection<Integer> skuIds);

	// 出貨時更新規格的庫存，回傳更新的筆數
	// stock、outbound_qty 各扣掉出貨數量，算法和訂單模組 SkuStockRepository 的 shipStock 相同；outbound_qty 不夠扣時扣到 0 為止
	// 庫存必須大於等於出貨數量：條件寫在同一個 UPDATE 裡，回傳 0 代表庫存不夠扣或查無此規格，呼叫端要丟例外讓整筆出貨還原
	// 必須在交易裡呼叫（ShippingService 的 shipOrders）
	@Modifying
	@Query(value = """
			UPDATE product_sku
			SET stock = COALESCE(stock, 0) - :quantity,
			    outbound_qty = GREATEST(COALESCE(outbound_qty, 0) - :quantity, 0),
			    updated_at = CURRENT_TIMESTAMP
			WHERE sku_id = :skuId
			  AND COALESCE(stock, 0) >= :quantity
			""", nativeQuery = true)
	int shipStock(@Param("skuId") Integer skuId, @Param("quantity") Integer quantity);

	// 取消出貨時把規格的庫存加回去，回傳更新的筆數
	// stock、outbound_qty 各加回出貨數量（整筆訂購數量）；出貨時 outbound_qty 若不夠扣只扣到 0，這裡仍然加回完整數量
	// 回傳 0 代表查無此規格，呼叫端要丟例外讓整筆取消出貨還原
	// 參數順序要和 shipStock 一樣是（規格編號、數量）：商品模組的 ProductAvailabilityScheduler 是照位置取值
	// 必須在交易裡呼叫（ShippingService 的 cancelShipOrders）
	@Modifying
	@Query(value = """
			UPDATE product_sku
			SET stock = COALESCE(stock, 0) + :quantity,
			    outbound_qty = COALESCE(outbound_qty, 0) + :quantity,
			    updated_at = CURRENT_TIMESTAMP
			WHERE sku_id = :skuId
			""", nativeQuery = true)
	int restoreStock(@Param("skuId") Integer skuId, @Param("quantity") Integer quantity);

}
