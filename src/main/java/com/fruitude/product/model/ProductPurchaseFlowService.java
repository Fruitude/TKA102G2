package com.fruitude.product.model;

import com.fruitude.po.model.*;
import com.fruitude.employee.model.Employee;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Inventory transactions and their locks belong to receiving/review, not this facade. */
@Service
public class ProductPurchaseFlowService {
    private final ReceivingService receiving;
    private final PoReviewService review;
    @Autowired
    public ProductPurchaseFlowService(ReceivingService receiving, PoReviewService review) {
        this.receiving=receiving; this.review=review;
    }
    // Preserve existing callers while removing the former outer transaction/locks.
    public ProductPurchaseFlowService(PoRepository pos, ReceivingService receiving, PoReviewService review,
            ProductInventoryLockService locks, ProductLifecycleService lifecycle, EntityManager em) {
        this(receiving,review);
    }
    public boolean receive(PoVO form, Employee employee, String receivedContent) {
        return receiving.receive(form,employee,receivedContent);
    }
    public void approve(Integer id, String reviewedContent) {
        review.approve(id,reviewedContent);
    }
}