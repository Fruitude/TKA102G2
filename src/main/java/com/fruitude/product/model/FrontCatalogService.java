package com.fruitude.product.model;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FrontCatalogService {
    private final ProductRepository repository;
    public FrontCatalogService(ProductRepository repository) { this.repository = repository; }

    public record SkuView(Integer skuId, String name, Integer price, Integer stock, Integer imageId) {}
    public record ProductView(Integer productId, String name, String description, Integer price,
        Integer skuId, Integer imageId, boolean giftBox, List<SkuView> skus, Integer stock) {}

    @Transactional(readOnly = true)
    public List<ProductView> getProducts() {
        List<ProductView> result = new ArrayList<>();
        for (Product p : repository.findByStatusOrderByProductIdAsc((byte) 1)) {
            List<SkuView> skus = p.getProductSkus().stream()
                .filter(s -> Byte.valueOf((byte) 1).equals(s.getStatus()) && s.getPrice() != null && s.getPrice() > 0)
                .sorted(Comparator.comparing(ProductSku::getSkuId))
                .map(s -> new SkuView(s.getSkuId(), s.getSkuName(), s.getPrice(), s.getStock(), firstImage(s))).toList();
            if (skus.isEmpty()) continue;
            SkuView cheapest = skus.stream().min(Comparator.comparing(SkuView::price).thenComparing(SkuView::skuId)).orElseThrow();
            boolean giftBox = p.getProductName() != null && p.getProductName().contains("禮盒");
            // Some fruit products identify gift packaging in SKU names rather than category names.
            if (skus.stream().anyMatch(s -> s.name() != null && s.name().contains("禮盒"))) giftBox = true;
            Set<Integer> visited = new HashSet<>();
            for (ProductCategory c = p.getProductCategory(); c != null && visited.add(c.getProductCategoryId()); c = c.getParentCategory()) {
                if (c.getCategoryName() != null && c.getCategoryName().contains("禮盒")) giftBox = true;
            }
            Integer imageId = cheapest.imageId();
            if (imageId == null) imageId = skus.stream().map(SkuView::imageId).filter(Objects::nonNull).findFirst().orElse(null);
            result.add(new ProductView(p.getProductId(), p.getProductName(), p.getProductDesc(), cheapest.price(),
                cheapest.skuId(), imageId, giftBox, skus, cheapest.stock()));
        }
        return result;
    }
    private Integer firstImage(ProductSku sku) {
        return sku.getProductImages().stream().filter(i -> i.getImageId() != null && i.getImageData() != null && i.getImageData().length > 0)
            .sorted(Comparator.comparing((ProductImage i) -> i.getSortOrder() == null ? 0 : i.getSortOrder()).thenComparing(ProductImage::getImageId))
            .map(ProductImage::getImageId).findFirst().orElse(null);
    }
}
