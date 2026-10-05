package com.fruitude.product.model;

/** Image identifiers only; image bytes are fetched when the thumbnail is displayed. */
public interface ProductOverviewImage {
    Integer getProductId();
    Integer getImageId();
}
