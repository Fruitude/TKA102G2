package com.fruitude.orders.model;
import java.util.Collection;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.fruitude.product.model.ProductSku;
public interface SkuStockRepository extends Repository<ProductSku,Integer> {
    @Query(value="SELECT sku_id FROM product_sku WHERE sku_id IN (:skuIds) ORDER BY sku_id FOR UPDATE",nativeQuery=true)
    List<Integer> lockSkus(@Param("skuIds") Collection<Integer> ids);
    @Query(value="SELECT product_id FROM product WHERE product_id IN (SELECT product_id FROM product_sku WHERE sku_id IN (:skuIds)) ORDER BY product_id FOR UPDATE",nativeQuery=true)
    List<Integer> lockProducts(@Param("skuIds") Collection<Integer> ids);
    @Modifying
    @Query(value="UPDATE product_sku SET outbound_qty=COALESCE(outbound_qty,0)+:qty,updated_at=CURRENT_TIMESTAMP "
        + "WHERE sku_id=:skuId AND :qty>0 AND status IN (1,2,3,6) "
        + "AND EXISTS(SELECT 1 FROM product p WHERE p.product_id=product_sku.product_id AND p.status=1) "
        + "AND :qty<=CASE WHEN status IN(2,3) THEN 5 ELSE 10 END "
        + "AND :qty<=FLOOR(GREATEST(0,COALESCE(stock,0)-COALESCE(outbound_qty,0) "
        + "+ CASE WHEN status=6 THEN 0 ELSE COALESCE(inbound_qty,0)*:yield END "
        + "+ CASE WHEN status IN(1,2) THEN GREATEST(COALESCE(max_backorder_qty,10),0) ELSE 0 END))",nativeQuery=true)
    int deductStock(@Param("skuId") Integer id,@Param("qty") Integer qty,@Param("yield") BigDecimal yield);
    @Modifying
    @Query(value="UPDATE product_sku SET stock=GREATEST(COALESCE(stock,0)-:qty,0),outbound_qty=GREATEST(COALESCE(outbound_qty,0)-:qty,0),updated_at=CURRENT_TIMESTAMP WHERE sku_id=:skuId",nativeQuery=true)
    int shipStock(@Param("skuId") Integer id,@Param("qty") Integer qty);
    @Modifying
    @Query(value="UPDATE product_sku SET outbound_qty=GREATEST(COALESCE(outbound_qty,0)-:qty,0),updated_at=CURRENT_TIMESTAMP WHERE sku_id=:skuId",nativeQuery=true)
    int releaseOutbound(@Param("skuId") Integer id,@Param("qty") Integer qty);
    @Modifying
    @Query(value="UPDATE product p SET p.status=1,p.updated_at=CURRENT_TIMESTAMP WHERE p.product_id IN(SELECT s.product_id FROM product_sku s WHERE s.sku_id IN(:skuIds)) AND p.status=0 AND p.auto_restock_enabled=1 AND EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.product_id AND s.status IN(1,2,3,6))",nativeQuery=true)
    int reopenProductsWithListedSkus(@Param("skuIds") Collection<Integer> ids);
    // Legacy service compatibility; active order flow uses releaseOutbound only.
    default int releaseStock(Integer id,Integer qty) { return releaseOutbound(id,qty); }
    @Modifying
    @Query(value="UPDATE product p SET p.status=0,p.updated_at=CURRENT_TIMESTAMP WHERE p.product_id IN(SELECT s.product_id FROM product_sku s WHERE s.sku_id IN(:skuIds)) AND p.status=1 AND NOT EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.product_id AND s.status IN(1,2,3,6))",nativeQuery=true)
    int closeProductsWithoutListedSkus(@Param("skuIds") Collection<Integer> ids);
}
