package com.fruitude.product.model;

/** Binary-only read for overview thumbnails, without SKU/product entity loading. */
public interface ProductThumbnailSource {
    byte[] getImageData();
}
