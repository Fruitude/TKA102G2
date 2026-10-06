package com.fruitude.product.model;

public class ProductUnavailableException extends IllegalArgumentException {
    public ProductUnavailableException(String message) { super(message); }
}
