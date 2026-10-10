package com.fruitude.orders.model;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.fruitude.product.model.ProductSku;

/**
 * 訂單庫存資料存取：鎖定規格，原子預留、出貨與釋放待出貨量。
 * 這些方法都必須在交易裡呼叫（OrdersService.placeOrder 的交易）。
 */
public interface SkuStockRepository extends Repository<ProductSku, Integer> {

	// 鎖住這些規格的資料列（FOR UPDATE），一直到交易結束（commit 或 rollback）才放開。
	// 其他交易如果也要鎖定讀取這些列的庫存（前台查庫存用 FOR SHARE），會等到這裡的交易結束才能讀。
	// 一定要依 sku_id 由小到大鎖，兩筆單單才不會因為鎖定順序相反而互相等待（死結）
	@Query(value = "SELECT sku_id FROM product_sku WHERE sku_id IN (:skuIds) ORDER BY sku_id FOR UPDATE", nativeQuery = true)
	List<Integer> lockSkus(@Param("skuIds") Collection<Integer> skuIds);

    // 單單只預留待出貨；1/2 不以實際庫存限制，3 不可超過剩餘可供應量。
    @Modifying
    @Query(value = "UPDATE product_sku SET outbound_qty=COALESCE(outbound_qty,0)+:qty, updated_at=CURRENT_TIMESTAMP "
        + "WHERE sku_id=:skuId AND :qty>0 AND status IN (1,2,3) "
        + "AND EXISTS (SELECT 1 FROM product p WHERE p.product_id=product_sku.product_id AND p.status=1) "
        + "AND ((status=1 AND :qty<=10) OR (status=2 AND :qty<=5) OR (status=3 AND :qty<=10 AND COALESCE(stock,0)+COALESCE(inbound_qty,0)-COALESCE(outbound_qty,0)>=:qty))", nativeQuery=true)
    int reserveStock(@Param("skuId") Integer skuId, @Param("qty") Integer qty);

    @Modifying
    @Query(value="UPDATE product_sku SET stock=stock-:qty, outbound_qty=outbound_qty-:qty, updated_at=CURRENT_TIMESTAMP WHERE sku_id=:skuId AND :qty>0 AND stock>=:qty AND outbound_qty>=:qty",nativeQuery=true)
    int shipStock(@Param("skuId") Integer skuId,@Param("qty") Integer qty);

    @Modifying
    @Query(value="UPDATE product_sku SET outbound_qty=outbound_qty-:qty, updated_at=CURRENT_TIMESTAMP WHERE sku_id=:skuId AND :qty>0 AND outbound_qty>=:qty",nativeQuery=true)
    int releaseStock(@Param("skuId") Integer skuId,@Param("qty") Integer qty);

    @Query(value = "SELECT product_id FROM product WHERE product_id IN (SELECT product_id FROM product_sku WHERE sku_id IN (:skuIds)) ORDER BY product_id FOR UPDATE", nativeQuery = true)
    List<Integer> lockProducts(@Param("skuIds") Collection<Integer> skuIds);

    @Modifying
    @Query(value = "UPDATE product_sku SET status = 5, updated_at = CURRENT_TIMESTAMP WHERE sku_id IN (:skuIds) AND status = 3 AND COALESCE(stock,0) + COALESCE(inbound_qty,0) - COALESCE(outbound_qty,0) <= 0", nativeQuery = true)
    int closeDepletedSkus(@Param("skuIds") Collection<Integer> skuIds);

    @Modifying
    @Query(value = "UPDATE product p SET p.status = 0, p.updated_at = CURRENT_TIMESTAMP WHERE p.product_id IN (SELECT s.product_id FROM product_sku s WHERE s.sku_id IN (:skuIds)) AND p.status = 1 AND NOT EXISTS (SELECT 1 FROM product_sku active WHERE active.product_id = p.product_id AND active.status IN (1,2,3))", nativeQuery = true)
    int closeProductsWithoutListedSkus(@Param("skuIds") Collection<Integer> skuIds);
}
