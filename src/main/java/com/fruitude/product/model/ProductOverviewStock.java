package com.fruitude.product.model;

/** Aggregated SKU stock flags without loading SKU entities or images. */
public interface ProductOverviewStock {
    Integer getProductId();
    Integer getBelowSafety();
    Integer getLowStock();
    Integer getHighStock();
    Integer getUnsetSafety();
    Integer getMaxInbound();
    Integer getMaxOutbound();
}
