package com.fruitude.product.model;

public class ProductStatusConfirmationException extends IllegalStateException {
    public ProductStatusConfirmationException() {
        super("商品沒有有效販售規格，是否將下架規格一併上架？售完及永久停產規格不會自動上架。");
    }
}
