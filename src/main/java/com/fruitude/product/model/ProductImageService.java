package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductImageService {

    @org.springframework.transaction.annotation.Transactional
    public void replaceImage(Integer id, Integer sortOrder, ProductImage replacement) {
        ProductImage original = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("圖片不存在"));
        original.setSortOrder(sortOrder);
        if (replacement.getImageData() != null) {
            original.setImageData(replacement.getImageData());
            original.setImageName(replacement.getImageName());
            original.setImageType(replacement.getImageType());
            original.setFileSize(replacement.getFileSize());
        }
        original.setUpdatedAt(java.time.LocalDateTime.now());
        repository.saveAndFlush(original);
    }

    @Autowired
    ProductImageRepository repository;

    public void addProductImage(ProductImage productImage) {
        repository.save(productImage);
    }

    public void updateProductImage(ProductImage productImage) {
        repository.save(productImage);
    }

    public void deleteProductImage(Integer imageId) {
        if (repository.existsById(imageId)) {
            repository.deleteById(imageId);
        }
    }

    public ProductImage getOneProductImage(Integer imageId) {
        Optional<ProductImage> optional =
                repository.findById(imageId);

        return optional.orElse(null);
    }

    public List<ProductImage> getAll() {
        return repository.findAll(org.springframework.data.domain.Sort.by("productSku.skuId", "sortOrder", "imageId"));
    }
}
