package com.fruitude.product.model;
public class ProductStatusAccessException extends IllegalArgumentException {
    public ProductStatusAccessException() { super("只有 ADMIN 系統管理員可以解除永久停產"); }
}
