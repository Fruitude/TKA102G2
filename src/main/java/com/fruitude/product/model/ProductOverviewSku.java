package com.fruitude.product.model;

/** SKU names for the products on the current overview page. */
public interface ProductOverviewSku {
    Integer getProductId();
    String getSkuName();
    String getAnotherName();
}
