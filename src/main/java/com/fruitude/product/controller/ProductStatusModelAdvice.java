package com.fruitude.product.controller;

import com.fruitude.product.model.ProductStatusAccess;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {ProductController.class, ProductSkuController.class})
public class ProductStatusModelAdvice {
    private final ProductStatusAccess access;
    public ProductStatusModelAdvice(ProductStatusAccess access) { this.access = access; }
    @ModelAttribute("canRestoreDiscontinued")
    public boolean canRestoreDiscontinued() { return access.canRestoreDiscontinued(); }
}
