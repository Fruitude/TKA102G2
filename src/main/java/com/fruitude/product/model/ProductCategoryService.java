package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductCategoryService {

    @Autowired
    ProductCategoryRepository repository;

    public void addProductCategory(ProductCategory productCategory) {
        repository.save(productCategory);
    }

    public void updateProductCategory(ProductCategory productCategory) {
        repository.save(productCategory);
    }

    public void deleteProductCategory(Integer productCategoryId) {
        if (repository.existsById(productCategoryId)) {
            repository.deleteById(productCategoryId);
        }
    }

    public ProductCategory getOneProductCategory(Integer productCategoryId) {
        Optional<ProductCategory> optional =
                repository.findById(productCategoryId);

        return optional.orElse(null);
    }

    public List<ProductCategory> getAll() {
        return repository.findAll();
    }
    
    
}
