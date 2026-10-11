package com.fruitude.product.model;

import java.util.*;
import com.fruitude.po.model.*;
import com.fruitude.employee.model.Employee;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates product rules around the team's unchanged approval and receiving implementations. */
@Service
public class ProductPurchaseFlowService {
    private final PoRepository pos;
    private final ReceivingService receiving;
    private final PoReviewService review;
    private final ProductInventoryLockService locks;
    private final ProductLifecycleService lifecycle;
    private final EntityManager em;
    public ProductPurchaseFlowService(PoRepository pos, ReceivingService receiving, PoReviewService review,
            ProductInventoryLockService locks, ProductLifecycleService lifecycle, EntityManager em) {
        this.pos=pos; this.receiving=receiving; this.review=review;
        this.locks=locks; this.lifecycle=lifecycle; this.em=em;
    }
    private List<Product> lockPurchaseProducts(Integer id) {
        if(id==null) throw new IllegalArgumentException("查無此採購單");
        // The PO is locked first, matching the team's edit/receive entry points.
        var po=pos.lockForReceiving(id).orElseThrow(()->new IllegalArgumentException("查無此採購單"));
        em.refresh(po);
        var ids=po.getPoDetails().stream().map(d->d.getSkuId().getProduct().getProductId()).distinct().sorted().toList();
        return ids.stream().map(pid->locks.lockProduct(pid)
            .orElseThrow(()->new IllegalArgumentException("商品不存在"))).toList();
    }
    @Transactional
    public boolean receive(PoVO form, Employee employee, String receivedContent) {
        var products=lockPurchaseProducts(form.getPoId());
        Map<Integer,Integer> stockBefore=new HashMap<>();
        products.forEach(p->p.getProductSkus().forEach(s->stockBefore.put(s.getSkuId(),zero(s.getStock()))));
        boolean modified=receiving.receive(form,employee,receivedContent);
        // Persist receipt history before querying yield. Bulk stock SQL leaves managed SKUs stale.
        em.flush();
        for(var p:products) {
            locks.reloadSkus(p);
            var received=new HashSet<Integer>();
            p.getProductSkus().stream().filter(s->zero(s.getStock())>stockBefore.get(s.getSkuId()))
                .forEach(s->received.add(s.getSkuId()));
            if(!received.isEmpty()) lifecycle.afterReceipt(p,received);
            lifecycle.synchronize(p);
        }
        em.flush();
        return modified;
    }
    @Transactional
    public void approve(Integer id, String reviewedContent) {
        var products=lockPurchaseProducts(id);
        var ids=products.stream().map(Product::getProductId).toList();
        review.approve(id,reviewedContent);
        // The team's approval deliberately clears the EntityManager after its conditional status update.
        // Reacquire managed entities; database locks remain held by this transaction.
        for(var pid:ids) {
            var p=locks.lockProduct(pid).orElseThrow(()->new IllegalArgumentException("商品不存在"));
            lifecycle.synchronize(p); // Pending deliveries alone never revive a sold-out SKU.
        }
        em.flush();
    }
    private static int zero(Integer value) { return value==null?0:value; }
}
