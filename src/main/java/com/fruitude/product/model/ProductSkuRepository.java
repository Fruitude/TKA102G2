// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Integer> {
    @Query("select s from ProductSku s join fetch s.product p where s.status in (1,2,3,4) and p.status<>2 and p.autoRestockEnabled=true order by s.skuId")
    List<ProductSku> findSupplyMonitoringCandidates();


	// 同一個商品底下，規格名稱是否已存在
    boolean existsByProduct_ProductIdAndSkuName(
            Integer productId,
            String skuName);

    // 修改時排除目前這筆 SKU，避免把自己判定為重複
    boolean existsByProduct_ProductIdAndSkuNameAndSkuIdNot(
            Integer productId,
            String skuName,
            Integer skuId);
}
