// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Integer> {

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
