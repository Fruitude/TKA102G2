package com.fruitude.product.model;

/** Read only list fields without loading SKU collections or image bytes. */
public interface ProductOverview {
    Integer getProductId();
    String getProductName();
    String getVendorName();
    Integer getStatus();
    Integer getAllCommentAmount();
    Integer getAllCommentStar();
    Integer getImageId();
}
