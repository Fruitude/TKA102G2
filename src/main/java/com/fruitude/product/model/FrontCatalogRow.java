package com.fruitude.product.model;

public interface FrontCatalogRow {
    Integer getProductId();
    String getName();
    String getDescription();
    Integer getCategoryId();
    Integer getSkuId();
    String getSkuName();
    String getAnotherName();
    Integer getPrice();
    Integer getStock();
    Integer getInboundQty();
    Integer getOutboundQty();
    Integer getSkuStatus();
}
