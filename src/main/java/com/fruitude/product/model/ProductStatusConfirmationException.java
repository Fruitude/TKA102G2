package com.fruitude.product.model;

public class ProductStatusConfirmationException extends IllegalStateException {
    public ProductStatusConfirmationException() {
        super("商品沒有上架規格，是否將未永久停產的規格一併上架？");
    }
}
