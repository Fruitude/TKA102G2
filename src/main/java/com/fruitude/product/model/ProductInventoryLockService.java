package com.fruitude.product.model;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Product-owned locking boundary. Always lock parents before SKUs and reload after locks. */
@Service
public class ProductInventoryLockService {
    private final ProductRepository products;
    private final EntityManager em;
    public ProductInventoryLockService(ProductRepository products, EntityManager em) {
        this.products=products; this.em=em;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<Product> lockProduct(Integer id) {
        var product=products.lockForStatus(id);
        if(product.isEmpty()) return product;
        // Native locks do not trust entities that a controller already loaded into the persistence context.
        em.createNativeQuery("SELECT sku_id FROM product_sku WHERE product_id=:id ORDER BY sku_id FOR UPDATE")
            .setParameter("id",id).getResultList();
        em.refresh(product.get());
        reloadSkus(product.get());
        return product;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void reloadSkus(Product product) {
        for(var sku:product.getProductSkus()) em.refresh(sku);
    }
}
