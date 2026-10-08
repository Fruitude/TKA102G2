// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

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

    // 某供應商底下所有商品的規格，排除指定狀態（採購單修改頁用來排除永久停產）
    List<ProductSku> findByProduct_Vendor_VendorIdAndStatusNotOrderBySkuIdAsc(
            Integer vendorId,
            Byte status);
}
