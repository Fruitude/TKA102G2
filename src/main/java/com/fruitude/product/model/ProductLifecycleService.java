package com.fruitude.product.model;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** Called within the product/SKU transaction; discontinued records never participate in automatic changes. */
@Service
public class ProductLifecycleService {
    public static final byte PRODUCT_DISCONTINUED = 2;
    public static final byte SKU_DISCONTINUED = 4;
    private final ProductStatusAccess access;
    public ProductLifecycleService(ProductStatusAccess access) { this.access = access; }

    public void changeProduct(Product product, Byte status, boolean activateSkus) {
        if (status == null || status < 0 || status > 2) throw new IllegalArgumentException("商品狀態須為 0～2");
        access.requireRestorePermission(product.getStatus(), status, PRODUCT_DISCONTINUED);
        closeDepletedSkus(product);
        var skus = product.getProductSkus();
        if (status == 1 && skus.stream().noneMatch(s -> isSellable(s.getStatus()))) {
            if (skus.stream().noneMatch(s -> !Byte.valueOf(SKU_DISCONTINUED).equals(s.getStatus())))
                throw new IllegalArgumentException("此商品沒有可上架規格，請先新增規格或由 ADMIN 解除規格永久停產");
            if (skus.stream().anyMatch(s -> Byte.valueOf((byte)5).equals(s.getStatus()))) {
                skus.stream().filter(s -> Byte.valueOf((byte)5).equals(s.getStatus())).forEach(s -> setSkuStatus(s, (byte)1));
            } else {
                if (!activateSkus) throw new ProductStatusConfirmationException();
                skus.stream().filter(s -> Byte.valueOf((byte)0).equals(s.getStatus())).forEach(s -> setSkuStatus(s, (byte)1));
            }
        }
        if (status == 0) skus.stream().filter(s -> !Byte.valueOf(SKU_DISCONTINUED).equals(s.getStatus()))
            .forEach(s -> setSkuStatus(s, (byte)0));
        product.setStatus(status);
        product.setUpdatedAt(LocalDateTime.now());
    }

    public void validateSkuChange(Byte previous, Byte next) {
        if (next == null || next < 0 || next > 5) throw new IllegalArgumentException("規格狀態須為 0～5");
        access.requireRestorePermission(previous, next, SKU_DISCONTINUED);
    }

    public void synchronize(Product product) {
        // Status 3 ends when stock plus inbound minus outbound reaches zero. Permanent retirement is untouched.
        closeDepletedSkus(product);
        if (Byte.valueOf(PRODUCT_DISCONTINUED).equals(product.getStatus())) return;
        byte status = (byte)(product.getProductSkus().stream().anyMatch(s -> isSellable(s.getStatus())) ? 1 : 0);
        if (!Byte.valueOf(status).equals(product.getStatus())) {
            product.setStatus(status); product.setUpdatedAt(LocalDateTime.now());
        }
    }
    public static boolean isDepleted(ProductSku sku) {
        return (sku.getStock() == null ? 0L : sku.getStock().longValue())
            + (sku.getInboundQty() == null ? 0L : sku.getInboundQty().longValue())
            - (sku.getOutboundQty() == null ? 0L : sku.getOutboundQty().longValue()) <= 0;
    }
    public static boolean isSellable(Byte status) { return status != null && status >= 1 && status <= 3; }
    public void closeDepletedSkus(Product product) {
        product.getProductSkus().stream().filter(s -> Byte.valueOf((byte)3).equals(s.getStatus())
            && isDepleted(s)).forEach(s -> setSkuStatus(s, (byte)0));
    }
    private void setSkuStatus(ProductSku sku, byte status) {
        if (!Byte.valueOf(status).equals(sku.getStatus())) {
            sku.setStatus(status); sku.setUpdatedAt(LocalDateTime.now());
        }
    }
}
