package com.fruitude.product.model;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fruitude.orders.model.CheckoutItem;

@Service
public class FrontCatalogService {
    private final ProductRepository repository;
    private final FrontCatalogCache cache = new FrontCatalogCache(Clock.systemUTC(), Duration.ofMinutes(10));
    public FrontCatalogService(ProductRepository repository) { this.repository = repository; }

    public record SkuView(Integer skuId, String name, Integer price, Integer stock, Integer imageId, List<Integer> imageIds, Integer skuStatus) {}
    public record ProductView(Integer productId, String name, String description, Integer price,
        Integer skuId, Integer imageId, boolean giftBox, List<SkuView> skus, Integer stock) {}
    public record LiveSku(Integer skuId, String name, String skuName, Integer price, int stock, boolean available, Integer skuStatus) {}

    public List<ProductView> getProducts() { return cache.get(() -> loadProducts(null)); }

    public ProductView getLiveProduct(Integer productId) {
        if (productId == null) return null;
        return loadProducts(productId).stream().findFirst().orElse(null);
    }

    private List<ProductView> loadProducts(Integer productId) {
        List<FrontCatalogRow> rows = repository.findFrontRows(productId);
        if (rows.isEmpty()) return List.of();
        Map<Integer, List<FrontCatalogRow>> groups = new LinkedHashMap<>();
        for (FrontCatalogRow row : rows) groups.computeIfAbsent(row.getProductId(), key -> new ArrayList<>()).add(row);
        Map<Integer, List<Integer>> images = new HashMap<>();
        for (FrontImageRow row : repository.findFrontImages(new ArrayList<>(groups.keySet())))
            images.computeIfAbsent(row.getSkuId(), key -> new ArrayList<>()).add(row.getImageId());
        Map<Integer, FrontCategoryRow> categories = new HashMap<>();
        for (FrontCategoryRow row : repository.findFrontCategories()) categories.put(row.getCategoryId(), row);
        List<ProductView> result = new ArrayList<>();
        for (List<FrontCatalogRow> group : groups.values()) {
            FrontCatalogRow product = group.get(0);
            List<SkuView> skus = group.stream().map(row -> {
                List<Integer> ids = List.copyOf(images.getOrDefault(row.getSkuId(), List.of()));
                return new SkuView(row.getSkuId(), ProductSku.resolveDisplayName(row.getSkuName(), row.getAnotherName()), row.getPrice(), quantityLimit(row.getSkuStatus(), row.getStock(), row.getInboundQty(), row.getOutboundQty()),
                    ids.isEmpty() ? null : ids.get(0), ids, row.getSkuStatus());
            }).toList();
            SkuView cheapest = skus.stream().min(Comparator.comparing(SkuView::price).thenComparing(SkuView::skuId)).orElseThrow();
            boolean giftBox = containsGift(product.getName()) || skus.stream().anyMatch(s -> containsGift(s.name())) || group.stream().anyMatch(row -> containsGift(row.getSkuName()));
            Set<Integer> visited = new HashSet<>();
            Integer categoryId = product.getCategoryId();
            while (categoryId != null && visited.add(categoryId)) {
                FrontCategoryRow category = categories.get(categoryId);
                if (category == null) break;
                if (containsGift(category.getName())) giftBox = true;
                categoryId = category.getParentId();
            }
            Integer imageId = cheapest.imageId();
            if (imageId == null) imageId = skus.stream().map(SkuView::imageId).filter(Objects::nonNull).findFirst().orElse(null);
            result.add(new ProductView(product.getProductId(), product.getName(), product.getDescription(), cheapest.price(),
                cheapest.skuId(), imageId, giftBox, skus, cheapest.stock()));
        }
        return List.copyOf(result);
    }

    // The stock view field is the order quantity limit, not physical stock.
    static int quantityLimit(Integer status, Integer stock, Integer inbound, Integer outbound) {
        if (Integer.valueOf(1).equals(status)) return 10;
        if (Integer.valueOf(2).equals(status)) return 10;
        if (Integer.valueOf(3).equals(status)) {
            long expected = (stock == null ? 0L : stock.longValue())
                + (inbound == null ? 0L : inbound.longValue()) - (outbound == null ? 0L : outbound.longValue());
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, expected));
        }
        return 0;
    }

    private boolean containsGift(String text) { return text != null && text.contains("禮盒"); }

    @Transactional(readOnly = true)
    public List<LiveSku> getLiveSkus(List<Integer> ids) {
        List<Integer> requested = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (requested.isEmpty()) return List.of();
        return repository.findLiveSkus(requested).stream().map(row -> {
            int stock = quantityLimit(row.getSkuStatus(), row.getStock(), row.getInboundQty(), row.getOutboundQty());
            boolean available = Integer.valueOf(1).equals(row.getProductStatus()) && row.getSkuStatus() != null && Set.of(1,2,3).contains(row.getSkuStatus())
                && row.getPrice() != null && row.getPrice() > 0 && stock > 0;
            return new LiveSku(row.getSkuId(), row.getName(), ProductSku.resolveDisplayName(row.getSkuName(), row.getAnotherName()), row.getPrice(), stock, available, row.getSkuStatus());
        }).toList();
    }

    public void validateCheckoutItems(List<CheckoutItem> items) {
        if (items == null || items.isEmpty()) throw new ProductUnavailableException("購物車沒有商品，請重新確認");
        Map<Integer, Long> quantities = new HashMap<>();
        for (CheckoutItem item : items) {
            if (item == null || item.getSkuId() == null || item.getQty() == null || item.getQty() <= 0)
                throw new ProductUnavailableException("商品或數量不正確，請返回購物車重新確認");
            quantities.merge(item.getSkuId(), item.getQty().longValue(), Long::sum);
        }
        Map<Integer, LiveSku> live = new HashMap<>();
        for (LiveSku sku : getLiveSkus(new ArrayList<>(quantities.keySet()))) live.put(sku.skuId(), sku);
        for (CheckoutItem item : items) {
            LiveSku sku = live.get(item.getSkuId());
            if (sku == null || !sku.available()) throw new ProductUnavailableException("商品已下架或已無可訂購數量，請返回購物車重新確認");
            if (quantities.get(sku.skuId()) > sku.stock()) throw new ProductUnavailableException(sku.name() + " 超過此規格的可訂購數量，請返回購物車調整數量");
            if (!Objects.equals(item.getPrice(), sku.price())) throw new ProductUnavailableException(sku.name() + " 價格已更新，請返回購物車重新確認");
            item.setProductName(sku.name());
            item.setPrice(sku.price());
        }
    }
}
