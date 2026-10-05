package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    @org.springframework.transaction.annotation.Transactional
    public void updateBasicFields(Product form) {
        Product existing = repository.findById(form.getProductId()).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        existing.setProductName(form.getProductName());
        existing.setProductDesc(form.getProductDesc());
        existing.setProductCategory(form.getProductCategory());
        existing.setVendor(form.getVendor());
        existing.setStatus(form.getStatus());
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        repository.saveAndFlush(existing);
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean updateStatus(Integer id, Byte status) {
        Product existing = repository.findById(id).orElse(null);
        if (existing == null) return false;
        existing.setStatus(status);
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        repository.saveAndFlush(existing);
        return true;
    }

    @org.springframework.transaction.annotation.Transactional
    public int updatePageStatus(List<Integer> productIds, Byte status) {
        if (status == null || (status != 0 && status != 1)) throw new IllegalArgumentException("狀態須為上架或下架");
        if (productIds == null || productIds.isEmpty() || productIds.size() > 100
                || productIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("請提供本頁有效的商品編號（最多100筆）");
        }
        var ids = new java.util.LinkedHashSet<>(productIds);
        if (repository.countByProductIdIn(ids) != ids.size()) throw new IllegalArgumentException("本頁商品已變更，請重新整理後再操作");
        int updated = repository.updatePageStatus(ids, status, java.time.LocalDateTime.now());
        if (updated != ids.size()) throw new IllegalStateException("本頁商品已變更，請重新整理後再操作");
        return updated;
    }

    public boolean isNameUsed(Integer vendorId, String name, Integer excludedId) {
        return excludedId == null ? repository.existsByVendor_VendorIdAndProductNameIgnoreCase(vendorId, name)
            : repository.existsByVendor_VendorIdAndProductNameIgnoreCaseAndProductIdNot(vendorId, name, excludedId);
    }

    @Autowired
    ProductRepository repository;

    public void addProduct(Product product) {
        repository.save(product);
    }

    public void updateProduct(Product product) {
        repository.save(product);
    }

    public void deleteProduct(Integer productId) {
        if (repository.existsById(productId)) {
            repository.deleteById(productId);
        }
    }

    public Product getOneProduct(Integer productId) {
        Optional<Product> optional =
                repository.findById(productId);

        return optional.orElse(null);
    }

    public List<Product> getAll() {
        return repository.findAll();
    }

    public org.springframework.data.domain.Page<ProductOverview> getOverviewPage(int page, int size, String statusFilter) {
        return getOverviewPage(page, size, statusFilter, null, null, null);
    }

    public java.util.Map<Integer, List<String>> getOverviewSkuNames(List<Integer> productIds) {
        var names = new java.util.LinkedHashMap<Integer, List<String>>();
        if (!productIds.isEmpty()) {
            for (var sku : repository.findOverviewSkuNames(productIds)) {
                names.computeIfAbsent(sku.getProductId(), id -> new java.util.ArrayList<>()).add(sku.getSkuName());
            }
        }
        return names;
    }

    public java.util.Map<Integer, List<Integer>> getOverviewImageIds(List<Integer> productIds) {
        var images = new java.util.LinkedHashMap<Integer, List<Integer>>();
        if (!productIds.isEmpty()) {
            for (var image : repository.findOverviewImages(productIds)) {
                images.computeIfAbsent(image.getProductId(), id -> new java.util.ArrayList<>()).add(image.getImageId());
            }
        }
        return images;
    }

    public java.util.Map<Integer, List<String>> getOverviewStockStatuses(List<Integer> productIds) {
        var statuses = new java.util.LinkedHashMap<Integer, List<String>>();
        productIds.forEach(id -> statuses.put(id, List.of("正常")));
        if (productIds.isEmpty()) return statuses;
        for (var stock : repository.findOverviewStock(productIds)) {
            var alerts = new java.util.ArrayList<String>();
            if (Integer.valueOf(1).equals(stock.getBelowSafety())) alerts.add("低於安全庫存");
            if (Integer.valueOf(1).equals(stock.getLowStock())) alerts.add("庫存低於10");
            if (Integer.valueOf(1).equals(stock.getHighStock())) alerts.add("庫存高於100");
            int inbound = stock.getMaxInbound() == null ? 0 : stock.getMaxInbound();
            int outbound = stock.getMaxOutbound() == null ? 0 : stock.getMaxOutbound();
            if (inbound > 100) alerts.add("待進貨超過100");
            else if (inbound > 50) alerts.add("待進貨超過50");
            if (outbound > 100) alerts.add("待出貨超過100");
            else if (outbound > 50) alerts.add("待出貨超過50");
            if (Integer.valueOf(1).equals(stock.getUnsetSafety())) alerts.add("未設安全庫存");
            statuses.put(stock.getProductId(), alerts.isEmpty() ? List.of("正常") : alerts);
        }
        return statuses;
    }

    public org.springframework.data.domain.Page<ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId) {
        return getOverviewPage(page, size, statusFilter, parentCategoryId, categoryId, vendorId, "all");
    }

    public static String normalizeStockFilter(String filter) {
        if ("low-stock".equals(filter)) return "below-safety";
        return filter != null && java.util.Set.of("all", "normal", "abnormal", "below-safety", "high-stock",
            "inbound", "outbound", "unset-safety").contains(filter) ? filter : "all";
    }

    public org.springframework.data.domain.Page<ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter) {
        return getOverviewPage(page, size, statusFilter, parentCategoryId, categoryId, vendorId, stockFilter, null, null, "");
    }

    public static Integer normalizeCommentCount(String value) {
        if (value == null || !value.matches("[0-9]+")) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    public static String normalizeRatingFilter(String value) {
        return value != null && java.util.Set.of("unrated", "0-1", "1-2", "2-3", "3-4", "4-5").contains(value) ? value : "";
    }

    public org.springframework.data.domain.Page<ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter,
            Integer minComments, Integer maxComments, String ratingFilter) {
        return getOverviewPage(page, size, statusFilter, parentCategoryId, categoryId, vendorId,
            stockFilter, minComments, maxComments, ratingFilter, "", "asc");
    }

    public static String normalizeOverviewSort(String value) {
        return java.util.Set.of("comments", "rating").contains(value == null ? "" : value) ? value : "";
    }

    public org.springframework.data.domain.Page<ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter,
            Integer minComments, Integer maxComments, String ratingFilter, String sortBy, String sortDirection) {
        sortBy = normalizeOverviewSort(sortBy);
        sortDirection = "desc".equals(sortDirection) ? "desc" : "asc";
        stockFilter = normalizeStockFilter(stockFilter);
        ratingFilter = normalizeRatingFilter(ratingFilter);
        Integer ratingBucket = ratingFilter.isEmpty() ? null : "unrated".equals(ratingFilter) ? -1 : ratingFilter.charAt(0) - '0';
        int safeSize = java.util.Set.of(10, 20, 50, 100).contains(size) ? size : 10;
        Integer productStatus = "on".equals(statusFilter) ? Integer.valueOf(1) : "off".equals(statusFilter) ? Integer.valueOf(0) : null;
        var result = repository.findOverviewPage(productStatus, parentCategoryId, categoryId, vendorId, stockFilter, minComments, maxComments, ratingBucket, sortBy, sortDirection,
            org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), safeSize));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            return repository.findOverviewPage(productStatus, parentCategoryId, categoryId, vendorId, stockFilter, minComments, maxComments, ratingBucket, sortBy, sortDirection,
            org.springframework.data.domain.PageRequest.of(result.getTotalPages() - 1, safeSize));
        }
        return result;
    }
}
