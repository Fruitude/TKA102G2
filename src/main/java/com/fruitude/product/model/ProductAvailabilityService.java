package com.fruitude.product.model;
import java.time.LocalDateTime;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProductAvailabilityService {
    private final ProductRepository products; private final ProductSkuRepository skus; private final ProductLifecycleService lifecycle;
    public ProductAvailabilityService(ProductRepository products,ProductSkuRepository skus,ProductLifecycleService lifecycle) {
        this.products=products;this.skus=skus;this.lifecycle=lifecycle;
    }
    @Transactional
    public int refresh(Integer id,LocalDateTime now) {
        var product=products.lockForStatus(id).orElse(null);
        if(product==null||Byte.valueOf((byte)2).equals(product.getStatus()))return 0;
        var before=product.getProductSkus().stream().map(ProductSku::getStatus).toList();
        var due=new HashSet<>(skus.findDueSkuIds(id,now));int activated=0;
        for(var sku:product.getProductSkus()) {
            if(Byte.valueOf((byte)5).equals(sku.getStatus())&&due.contains(sku.getSkuId())) {
                sku.setStatus((byte)1);sku.setUpdatedAt(now);activated++;
            }
        }
        lifecycle.closeDepletedSkus(product);
        if (before.equals(product.getProductSkus().stream().map(ProductSku::getStatus).toList())) return activated;
        lifecycle.synchronize(product);products.saveAndFlush(product);return activated;
    }
}
