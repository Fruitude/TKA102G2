// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Integer> {

    @Query(value="SELECT DISTINCT s.product_id FROM product_sku s JOIN product p ON p.product_id=s.product_id "
        + "WHERE p.status<>2 AND ((p.status=1 AND s.status IN (1,2)) "
        + "OR (s.status=3 AND COALESCE(s.stock,0)+COALESCE(s.inbound_qty,0)-COALESCE(s.outbound_qty,0)<=0)) ORDER BY s.product_id", nativeQuery=true)
    List<Integer> findAvailabilityProductIds();

    @Query(value="SELECT DISTINCT s.product_id FROM product_sku s JOIN product p ON p.product_id=s.product_id "
        + "JOIN promotion promotion ON promotion.sku_id=s.sku_id JOIN promo_project activity ON activity.promo_project_id=promotion.promo_project_id "
        + "WHERE s.status=6 AND p.status<>2 AND activity.status=1 AND activity.promo_type='SKU' "
        + "AND activity.promo_project_start<=:now AND (activity.promo_project_end IS NULL OR activity.promo_project_end>:now) ORDER BY s.product_id LIMIT 200",nativeQuery=true)
    List<Integer> findDueProductIds(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    @Query(value="SELECT DISTINCT s.sku_id FROM product_sku s JOIN promotion promotion ON promotion.sku_id=s.sku_id "
        + "JOIN promo_project activity ON activity.promo_project_id=promotion.promo_project_id "
        + "WHERE s.product_id=:productId AND s.status=6 AND activity.status=1 AND activity.promo_type='SKU' "
        + "AND activity.promo_project_start<=:now AND (activity.promo_project_end IS NULL OR activity.promo_project_end>:now)",nativeQuery=true)
    List<Integer> findDueSkuIds(@org.springframework.data.repository.query.Param("productId") Integer productId,
        @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

	// 同一個商品底下，規格名稱是否已存在
    boolean existsByProduct_ProductIdAndSkuName(
            Integer productId,
            String skuName);

    // 修改時排除目前這筆 SKU，避免把自己判定為重複
    boolean existsByProduct_ProductIdAndSkuNameAndSkuIdNot(
            Integer productId,
            String skuName,
            Integer skuId);

    // 某供應商底下所有商品的規格，排除指定狀態（採購單修改頁用來排除永久停產）
    List<ProductSku> findByProduct_Vendor_VendorIdAndStatusNotOrderBySkuIdAsc(
            Integer vendorId,
            Byte status);
}
