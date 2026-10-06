package com.fruitude.product.model;

public interface LiveSkuRow {
    Integer getSkuId();
    String getName();
    String getSkuName();
    Integer getPrice();
    Integer getStock();
    Integer getInboundQty();
    Integer getOutboundQty();
    Integer getProductStatus();
    Integer getSkuStatus();
}
