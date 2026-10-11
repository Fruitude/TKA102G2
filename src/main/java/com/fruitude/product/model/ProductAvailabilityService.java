package com.fruitude.product.model;
import java.time.LocalDateTime;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProductAvailabilityService {
    @org.springframework.beans.factory.annotation.Autowired
    private ProductInventoryLockService inventoryLocks;
    private java.util.Optional<Product> lockProduct(Integer id) {
        return inventoryLocks==null ? products.lockForStatus(id) : inventoryLocks.lockProduct(id);
    }

    private final ProductRepository products; private final ProductSkuRepository skus; private final ProductLifecycleService lifecycle;
    public ProductAvailabilityService(ProductRepository products,ProductSkuRepository skus,ProductLifecycleService lifecycle) {
        this.products=products;this.skus=skus;this.lifecycle=lifecycle;
    }
    @Transactional
    public int refresh(Integer id,LocalDateTime now) {
        var product=lockProduct(id).orElse(null);
        if(product==null)return 0;
        if(Byte.valueOf((byte)2).equals(product.getStatus())) {
            var retiredBefore=product.getProductSkus().stream().map(ProductSku::getStatus).toList();
            lifecycle.synchronize(product);
            if(!retiredBefore.equals(product.getProductSkus().stream().map(ProductSku::getStatus).toList()))products.saveAndFlush(product);
            return 0; // Retired products cannot activate prepared promotional SKUs.
        }
        var before=product.getProductSkus().stream().map(ProductSku::getStatus).toList();
        var productBefore=product.getStatus();
        var due=new HashSet<>(skus.findDueSkuIds(id,now));int activated=0;
        for(var sku:product.getProductSkus()) {
            if(Byte.valueOf((byte)5).equals(sku.getStatus())&&due.contains(sku.getSkuId())) {
                sku.setStatus((byte)1);sku.setUpdatedAt(now);activated++;
            }
        }
        lifecycle.synchronize(product);
        if (before.equals(product.getProductSkus().stream().map(ProductSku::getStatus).toList())
            && java.util.Objects.equals(productBefore,product.getStatus())) return activated;
        products.saveAndFlush(product);return activated;
    }
}
