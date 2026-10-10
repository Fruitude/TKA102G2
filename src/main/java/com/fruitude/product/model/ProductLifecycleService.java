package com.fruitude.product.model;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** Called within the product/SKU transaction; discontinued records never participate in automatic changes. */
@Service
public class ProductLifecycleService {
    public static final byte PRODUCT_DISCONTINUED = 2;
    public static final byte SKU_DISCONTINUED = 7;
    public static final byte SKU_SOLD_OUT = 4;
    public static final byte SKU_PREPARED = 5;
    private final ProductStatusAccess access;
    public ProductLifecycleService(ProductStatusAccess access) { this.access = access; }

    public void changeProduct(Product product, Byte status, boolean activateSkus) {
        if (status == null || status < 0 || status > 2) throw new IllegalArgumentException("商品狀態須為 0～2");
        access.requireRestorePermission(product.getStatus(), status, PRODUCT_DISCONTINUED);
        closeDepletedSkus(product);
        var skus = product.getProductSkus();
        if (status == 1 && skus.stream().noneMatch(s -> isSellable(s.getStatus()))) {
            if (skus.stream().noneMatch(s -> Byte.valueOf((byte)0).equals(s.getStatus()) || Byte.valueOf(SKU_PREPARED).equals(s.getStatus())))
                throw new IllegalArgumentException("此商品沒有可自動上架規格，請先新增規格；售完規格需確認貨源後手動上架，永久停產規格需由 ADMIN 解除");
            if (skus.stream().anyMatch(s -> Byte.valueOf(SKU_PREPARED).equals(s.getStatus()))) {
                skus.stream().filter(s -> Byte.valueOf(SKU_PREPARED).equals(s.getStatus())).forEach(s -> setSkuStatus(s, (byte)1));
            } else {
                if (!activateSkus) throw new ProductStatusConfirmationException();
                skus.stream().filter(s -> Byte.valueOf((byte)0).equals(s.getStatus())).forEach(s -> setSkuStatus(s, (byte)1));
            }
        }
        if (status == 0) skus.stream().filter(s -> isSellable(s.getStatus()) || Byte.valueOf(SKU_PREPARED).equals(s.getStatus()))
            .forEach(s -> setSkuStatus(s, (byte)0));
        product.setAutoRestockEnabled(status == 1);
        product.setStatus(status);
        product.setUpdatedAt(LocalDateTime.now());
        clearFrontCacheAfterCommit();
    }

    public void validateSkuChange(Byte previous, Byte next) {
        validateSkuChange(previous, next, false);
    }

    public void validateSkuChange(Byte previous, Byte next, boolean restockConfirmed) {
        if (next == null || next < 0 || next > 7) throw new IllegalArgumentException("規格狀態須為 0～7");
        access.requireRestorePermission(previous, next, SKU_DISCONTINUED);
        if (Byte.valueOf(SKU_SOLD_OUT).equals(previous) && !Byte.valueOf(SKU_SOLD_OUT).equals(next) && !restockConfirmed)
            throw new SkuRestockConfirmationException(next);
        if (Byte.valueOf(SKU_PREPARED).equals(next) && previous != null && !Byte.valueOf(SKU_PREPARED).equals(previous))
            throw new IllegalArgumentException("預備上架僅能於新增規格時設定，離開後不可再切回。");
    }

    public void synchronize(Product product) {
        // Status 3 ends when stock plus inbound minus outbound reaches zero. Permanent retirement is untouched.
        closeDepletedSkus(product);
        if (Byte.valueOf(PRODUCT_DISCONTINUED).equals(product.getStatus())) { clearFrontCacheAfterCommit(); return; }
        byte status = (byte)(product.getProductSkus().stream().anyMatch(s -> isSellable(s.getStatus())) ? 1 : 0);
        if (!Byte.valueOf(status).equals(product.getStatus())) {
            product.setStatus(status); product.setUpdatedAt(LocalDateTime.now());
        }
        if(status==1)product.setAutoRestockEnabled(true);
        clearFrontCacheAfterCommit();
    }
    public static boolean isDepleted(ProductSku sku) {
        return (sku.getStock() == null ? 0L : sku.getStock().longValue())
            + (sku.getInboundQty() == null ? 0L : sku.getInboundQty().longValue())
            - (sku.getOutboundQty() == null ? 0L : sku.getOutboundQty().longValue()) <= 0;
    }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private FrontCatalogService frontCatalog;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private SkuSupplyService supply;
    public void refreshSupplyStates(java.util.Collection<Integer> ids) {
        if (supply != null && supply.refreshLocked(ids)>0) clearFrontCacheAfterCommit();
    }
    public void clearFrontCacheAfterCommit() {
        if (frontCatalog == null) return;
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { frontCatalog.clearCache(); }
                });
        } else frontCatalog.clearCache();
    }
    public static boolean isSellable(Byte status) { return status != null && ((status >= 1 && status <= 3) || status == 6); }
    public void closeDepletedSkus(Product product) {
        if (Byte.valueOf(PRODUCT_DISCONTINUED).equals(product.getStatus())) return;
        var candidates=product.getProductSkus().stream().filter(s -> isSellable(s.getStatus())).toList();
        var ids=candidates.stream().map(ProductSku::getSkuId).filter(java.util.Objects::nonNull).toList();
        var rates=supply==null?java.util.Map.<Integer,java.math.BigDecimal>of():supply.yields(ids);
        for(var sku:candidates) setSkuStatus(sku,SkuSupplyService.nextStatus(sku,
            sku.getSkuId()==null?SkuSupplyService.DEFAULT_YIELD:rates.getOrDefault(sku.getSkuId(),SkuSupplyService.DEFAULT_YIELD),false));
    }
    public void afterReceipt(Product product, java.util.Set<Integer> receivedSkuIds) {
        if (product.getStatus()==2 || !Boolean.TRUE.equals(product.getAutoRestockEnabled())) return;
        var rates=supply==null?java.util.Map.<Integer,java.math.BigDecimal>of():supply.yields(receivedSkuIds);
        for(var sku:product.getProductSkus()) if(receivedSkuIds.contains(sku.getSkuId()))
            setSkuStatus(sku,SkuSupplyService.nextStatus(sku,rates.getOrDefault(sku.getSkuId(),SkuSupplyService.DEFAULT_YIELD),true));
        synchronize(product);
    }
    private void setSkuStatus(ProductSku sku, byte status) {
        if (!Byte.valueOf(status).equals(sku.getStatus())) {
            sku.setStatus(status); sku.setUpdatedAt(LocalDateTime.now());
        }
    }
}
