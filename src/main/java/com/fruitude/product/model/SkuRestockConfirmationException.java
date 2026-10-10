package com.fruitude.product.model;
public class SkuRestockConfirmationException extends IllegalArgumentException {
    public SkuRestockConfirmationException() { this((byte)1); }
    public SkuRestockConfirmationException(Byte next) {
        super(Byte.valueOf((byte)1).equals(next) ? "該商品規格原為售完商品，請確認是否有貨源" : "該商品規格原為售完商品，請確認是否變更規格狀態。");
    }
}
