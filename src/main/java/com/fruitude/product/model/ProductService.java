package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import com.fruitude.orders.model.CheckoutItem;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.fruitude.vendor.model.VendorRepository;
import java.util.regex.Pattern;
import com.fruitude.product.model.ProductService.ProductView;
import tools.jackson.core.*;
import tools.jackson.databind.*;

@Service
@org.springframework.scheduling.annotation.EnableScheduling
public class ProductService {
    private record RankedCatalog(List<ProductView> bestSellers, List<ProductView> topRated,
            List<Integer> bestIds, List<Integer> ratedIds, Map<Integer,ProductView> cards) {}
    private volatile RankedCatalog rankedCatalog;

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProductView> getBestSellers() { return cachedRankedCatalog().bestSellers(); }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProductView> getTopRatedProducts() { return cachedRankedCatalog().topRated(); }

    private RankedCatalog cachedRankedCatalog() {
        var snapshot = rankedCatalog;
        if (snapshot == null) synchronized (this) {
            if (rankedCatalog == null) refreshRankedCatalog();
            snapshot = rankedCatalog;
        }
        return snapshot;
    }

    // Each refresh replaces both immutable Lists together; readers never see a partial refresh.
    @org.springframework.scheduling.annotation.Scheduled(cron="0 0 0 * * *", zone="Asia/Taipei")
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public synchronized void refreshRankedCatalog() {
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
        var fromDate = now.toLocalDate().minusMonths(6).atStartOfDay();
        var products = new HashMap<Integer, ProductView>();
        for (var product : loadProducts(null)) products.put(product.productId(), product);
        var bestIds = repository.findBestSellerProductIds(fromDate, now);
        var ratedIds = repository.findTopRatedProductIds();
        var best = bestIds.stream().distinct().map(products::get).filter(Objects::nonNull).toList();
        var rated = ratedIds.stream().distinct().map(products::get).filter(Objects::nonNull).toList();
        rankedCatalog = new RankedCatalog(List.copyOf(best), List.copyOf(rated),
            List.copyOf(bestIds), List.copyOf(ratedIds), Map.copyOf(products));
    }

    // Refresh card contents without changing the daily sales/review ranking.
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public synchronized void refreshRankedProductCards(Collection<Integer> ids) {
        var previous=rankedCatalog;
        if(previous==null || ids.isEmpty()) return;
        var cards=new HashMap<>(previous.cards());
        for(var id:ids) {
            cards.remove(id);
            for(var card:loadProducts(id)) cards.put(card.productId(),card);
        }
        rankedCatalog=new RankedCatalog(
            previous.bestIds().stream().distinct().map(cards::get).filter(Objects::nonNull).toList(),
            previous.ratedIds().stream().distinct().map(cards::get).filter(Objects::nonNull).toList(),
            previous.bestIds(),previous.ratedIds(),Map.copyOf(cards));
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void initializeRankedCatalog() { cachedRankedCatalog(); }

    public ProductService() {}

    @org.springframework.beans.factory.annotation.Autowired
    private ProductInventoryLockService inventoryLocks;
    private java.util.Optional<Product> lockProduct(Integer id) {
        return inventoryLocks==null ? repository.lockForStatus(id) : inventoryLocks.lockProduct(id);
    }


    @Autowired
    ProductLifecycleService lifecycle;

    @org.springframework.transaction.annotation.Transactional
    public void updateBasicFields(Product form) { updateBasicFields(form, false); }

    @org.springframework.transaction.annotation.Transactional
    public void updateBasicFields(Product form, boolean activateSkus) {
        Product existing = lockProduct(form.getProductId()).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        lifecycle.changeProduct(existing, form.getStatus(), activateSkus);
        existing.setProductName(form.getProductName()); existing.setProductDesc(form.getProductDesc());
        existing.setProductCategory(form.getProductCategory()); existing.setVendor(form.getVendor());
        repository.saveAndFlush(existing);
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean updateStatus(Integer id, Byte status) { return updateStatus(id, status, false); }

    @org.springframework.transaction.annotation.Transactional
    public boolean updateStatus(Integer id, Byte status, boolean activateSkus) {
        Product existing = lockProduct(id).orElse(null);
        if (existing == null) return false;
        lifecycle.changeProduct(existing, status, activateSkus);
        repository.saveAndFlush(existing);
        return true;
    }

    // Each page item commits independently; the batch coordinator calls this Spring bean through its proxy.
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ProductPageStatusService.Item updatePageItemStatus(Integer id, Byte status, boolean activateSkus) {
        if(status==null || (status!=0 && status!=1)) throw new IllegalArgumentException("狀態須為上架或下架");
        var product=lockProduct(id).orElseThrow(()->new IllegalArgumentException("商品不存在，可能已被刪除"));
        if(Byte.valueOf((byte)2).equals(product.getStatus()))
            return new ProductPageStatusService.Item(id,product.getProductName(),product.getStatus(),"SKIPPED","商品已永久停產，批次操作略過");
        lifecycle.changeProduct(product,status,activateSkus);
        repository.saveAndFlush(product);
        String message=status==1 && !Byte.valueOf((byte)1).equals(product.getStatus())
            ? "無可販售規格，依供應狀況維持下架" : null;
        return new ProductPageStatusService.Item(id,product.getProductName(),product.getStatus(),"UPDATED",message);
    }

    public boolean isNameUsed(Integer vendorId, String name, Integer excludedId) {
        return excludedId == null ? repository.existsByVendor_VendorIdAndProductNameIgnoreCase(vendorId, name)
            : repository.existsByVendor_VendorIdAndProductNameIgnoreCaseAndProductIdNot(vendorId, name, excludedId);
    }

    @Autowired
    ProductRepository repository;

    @org.springframework.transaction.annotation.Transactional
    public void addProduct(Product product) { addProduct(product, false); }

    @org.springframework.transaction.annotation.Transactional
    public void addProduct(Product product, boolean activateSkus) {
        if (product.getStatus() != 2 && product.getProductSkus().stream().anyMatch(s -> ProductLifecycleService.isSellable(s.getStatus()) && !(s.getStatus() == 3 && ProductLifecycleService.isDepleted(s)))) product.setStatus((byte)1);
        lifecycle.changeProduct(product, product.getStatus(), activateSkus);
        // A sellable SKU takes precedence on creation, unless the product is permanently retired.
        if (product.getStatus() != 2 && product.getProductSkus().stream().anyMatch(s -> ProductLifecycleService.isSellable(s.getStatus()) && !(s.getStatus() == 3 && ProductLifecycleService.isDepleted(s)))) lifecycle.synchronize(product);
        repository.saveAndFlush(product);
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateProduct(Product product) {
        updateBasicFields(product);
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

    public org.springframework.data.domain.Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String statusFilter) {
        return getOverviewPage(page, size, statusFilter, null, null, null);
    }

    public java.util.Map<Integer, List<String>> getOverviewSkuNames(List<Integer> productIds) {
        var names = new java.util.LinkedHashMap<Integer, List<String>>();
        if (!productIds.isEmpty()) {
            for (var sku : repository.findOverviewSkuNames(productIds)) {
                names.computeIfAbsent(sku.getProductId(), id -> new java.util.ArrayList<>()).add(ProductSku.resolveDisplayName(sku.getSkuName(), sku.getAnotherName()));
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

    public org.springframework.data.domain.Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId) {
        return getOverviewPage(page, size, statusFilter, parentCategoryId, categoryId, vendorId, "all");
    }

    public static String normalizeStockFilter(String filter) {
        if ("low-stock".equals(filter)) return "below-safety";
        return filter != null && java.util.Set.of("all", "normal", "abnormal", "below-safety", "high-stock",
            "inbound", "outbound", "unset-safety").contains(filter) ? filter : "all";
    }

    public org.springframework.data.domain.Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter) {
        return getOverviewPage(page, size, statusFilter, parentCategoryId, categoryId, vendorId, stockFilter, null, null, "");
    }

    public static Integer normalizeCommentCount(String value) {
        if (value == null) return null;
        var normalized = new StringBuilder(value.length());
        for (char digit : value.toCharArray()) {
            normalized.append(digit >= '０' && digit <= '９' ? (char)(digit - '０' + '0') : digit);
        }
        value = normalized.toString();
        if (!value.matches("[0-9]+")) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    public static String normalizeRatingFilter(String value) {
        return value != null && java.util.Set.of("unrated", "0-1", "1-2", "2-3", "3-4", "4-5").contains(value) ? value : "";
    }

    public org.springframework.data.domain.Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter,
            Integer minComments, Integer maxComments, String ratingFilter) {
        return getOverviewPage(page, size, statusFilter, parentCategoryId, categoryId, vendorId,
            stockFilter, minComments, maxComments, ratingFilter, "", "asc");
    }

    public static String normalizeOverviewSort(String value) {
        return java.util.Set.of("comments", "rating", "modified").contains(value == null ? "" : value) ? value : "";
    }

    public org.springframework.data.domain.Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter,
            Integer minComments, Integer maxComments, String ratingFilter, String sortBy, String sortDirection) {
        return getOverviewPage(page, size, statusFilter, parentCategoryId, categoryId, vendorId, stockFilter,
            minComments, maxComments, ratingFilter, sortBy, sortDirection, "", true, true);
    }

    public org.springframework.data.domain.Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter,
            Integer minComments, Integer maxComments, String ratingFilter, String sortBy, String sortDirection,
            String keyword, boolean searchProductName, boolean searchSkuName) {
        if (!searchProductName && !searchSkuName) searchProductName = true;
        // Treat SQL wildcard characters as literal input, while matching partial names.
        String searchPattern = (keyword == null ? "" : keyword.strip()).toLowerCase(java.util.Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        sortBy = normalizeOverviewSort(sortBy);
        sortDirection = "desc".equals(sortDirection) ? "desc" : "asc";
        stockFilter = normalizeStockFilter(stockFilter);
        ratingFilter = normalizeRatingFilter(ratingFilter);
        Integer ratingBucket = ratingFilter.isEmpty() ? null : "unrated".equals(ratingFilter) ? -1 : ratingFilter.charAt(0) - '0';
        int safeSize = java.util.Set.of(10, 20, 50, 100).contains(size) ? size : 10;
        Integer productStatus = "on".equals(statusFilter) ? Integer.valueOf(1) : "off".equals(statusFilter) ? Integer.valueOf(0) : "discontinued".equals(statusFilter) ? Integer.valueOf(2) : null;
        var result = repository.findOverviewPage(productStatus, parentCategoryId, categoryId, vendorId, stockFilter, minComments, maxComments, ratingBucket, sortBy, sortDirection, searchPattern, searchProductName, searchSkuName,
            org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), safeSize));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            return repository.findOverviewPage(productStatus, parentCategoryId, categoryId, vendorId, stockFilter, minComments, maxComments, ratingBucket, sortBy, sortDirection, searchPattern, searchProductName, searchSkuName,
            org.springframework.data.domain.PageRequest.of(result.getTotalPages() - 1, safeSize));
        }
        return result;
    }

    // Front catalog and live SKU reads

    public ProductService(ProductRepository repository) { this.repository=repository; }
    @org.springframework.beans.factory.annotation.Autowired(required=false) private SkuSupplyService supply;


    // 指定商品促銷的活動價查詢（promo 套件）。用選擇性欄位注入，沒有它（例如單元測試）就不套用活動價
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fruitude.promo.model.PromoPriceService promoPriceService;

    // price 是前台實際價格（有活動價就是活動價），originalPrice 是規格原價
    public record SkuView(Integer skuId, String name, Integer price, Integer originalPrice, Integer stock, Integer imageId, List<Integer> imageIds, Integer skuStatus) {}
    public record ProductView(Integer productId, String name, String description, Integer price, Integer originalPrice,
        Integer skuId, Integer imageId, boolean giftBox, List<SkuView> skus, Integer stock, Double averageRating) {}
    public record LiveSku(Integer skuId, String name, String skuName, Integer price, Integer originalPrice, int stock, boolean available, Integer skuStatus) {}

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProductView> getProducts() { return loadProducts(null); }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProductView> getAllProducts(Integer memberId, Collection<Integer> browserFavoriteIds) {
        return rankCatalog(loadProducts(null), repository.findCatalogPriorities(memberId),
            memberId != null, browserFavoriteIds);
    }

    public static List<ProductView> rankCatalog(List<ProductView> products,
            List<ProductRepository.CatalogPriorityRow> priorities, boolean loggedIn,
            Collection<Integer> browserFavoriteIds) {
        Map<Integer, ProductRepository.CatalogPriorityRow> byId = new HashMap<>();
        for (var row : priorities) byId.put(row.getProductId(), row);
        Set<Integer> favoriteCategories = new HashSet<>();
        if (loggedIn && browserFavoriteIds != null) for (Integer id : browserFavoriteIds) {
            var row = byId.get(id);
            if (row != null && row.getCategoryId() != null) favoriteCategories.add(row.getCategoryId());
        }
        List<ProductView> result = new ArrayList<>(products);
        // Shuffle before stable sorting: ties and all remaining products are random.
        Collections.shuffle(result);
        result.sort(Comparator.comparingInt(product -> {
            var row = byId.get(product.productId());
            if (row == null) return 4;
            boolean favorite = Integer.valueOf(1).equals(row.getFavoriteMatch())
                || favoriteCategories.contains(row.getCategoryId());
            return catalogPriority(loggedIn, favorite, Integer.valueOf(1).equals(row.getOrderMatch()),
                Integer.valueOf(1).equals(row.getAboveSafety()));
        }));
        return List.copyOf(result);
    }

    public static int catalogPriority(boolean loggedIn, boolean favorite, boolean ordered, boolean aboveSafety) {
        if (!loggedIn) return aboveSafety ? 0 : 4;
        if (favorite && ordered && aboveSafety) return 0;
        if (favorite && aboveSafety) return 1;
        if (ordered && aboveSafety) return 2;
        if (favorite && ordered) return 3;
        return 4;
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ProductView getLiveProduct(Integer productId) {
        if (productId == null) return null;
        return loadProducts(productId).stream().findFirst().orElse(null);
    }

    private List<ProductView> loadProducts(Integer productId) {
        List<com.fruitude.product.model.ProductRepository.FrontCatalogRow> rows = repository.findFrontRows(productId);
        if (rows.isEmpty()) return List.of();
        var rates=supply==null?Map.<Integer,java.math.BigDecimal>of():supply.yields(rows.stream().map(com.fruitude.product.model.ProductRepository.FrontCatalogRow::getSkuId).toList());
        Map<Integer, Integer> promoPrices = activePromoPrices(rows.stream().map(com.fruitude.product.model.ProductRepository.FrontCatalogRow::getSkuId).toList());
        Map<Integer, List<com.fruitude.product.model.ProductRepository.FrontCatalogRow>> groups = new LinkedHashMap<>();
        for (com.fruitude.product.model.ProductRepository.FrontCatalogRow row : rows) groups.computeIfAbsent(row.getProductId(), key -> new ArrayList<>()).add(row);
        Map<Integer, List<Integer>> images = new HashMap<>();
        for (com.fruitude.product.model.ProductRepository.FrontImageRow row : repository.findFrontImages(new ArrayList<>(groups.keySet())))
            images.computeIfAbsent(row.getSkuId(), key -> new ArrayList<>()).add(row.getImageId());
        Map<Integer, com.fruitude.product.model.ProductRepository.FrontCategoryRow> categories = new HashMap<>();
        for (com.fruitude.product.model.ProductRepository.FrontCategoryRow row : repository.findFrontCategories()) categories.put(row.getCategoryId(), row);
        List<ProductView> result = new ArrayList<>();
        for (List<com.fruitude.product.model.ProductRepository.FrontCatalogRow> group : groups.values()) {
            com.fruitude.product.model.ProductRepository.FrontCatalogRow product = group.get(0);
            List<SkuView> skus = group.stream().map(row -> {
                List<Integer> ids = List.copyOf(images.getOrDefault(row.getSkuId(), List.of()));
           return new SkuView(row.getSkuId(), ProductSku.resolveDisplayName(row.getSkuName(), row.getAnotherName()) + (Integer.valueOf(4).equals(row.getSkuStatus()) ? "（售完）" : (Integer.valueOf(6).equals(row.getSkuStatus()) ? "（即將停產）" : "")), promoPrices.getOrDefault(row.getSkuId(), row.getPrice()), row.getPrice(), quantityLimit(row.getSkuStatus(), row.getStock(), row.getInboundQty(), row.getOutboundQty(), row.getMaxBackorderQty(),rates.getOrDefault(row.getSkuId(),SkuSupplyService.DEFAULT_YIELD)),
                    ids.isEmpty() ? null : ids.get(0), ids, row.getSkuStatus());
            }).toList();
            SkuView cheapest = skus.stream().filter(s -> s.stock() > 0 && Set.of(1,2,3,6).contains(s.skuStatus())).min(Comparator.comparing(SkuView::price).thenComparing(SkuView::skuId)).orElse(null);
            if (cheapest == null) continue;
            boolean giftBox = containsGift(product.getName()) || skus.stream().anyMatch(s -> containsGift(s.name())) || group.stream().anyMatch(row -> containsGift(row.getSkuName()));
            Set<Integer> visited = new HashSet<>();
            Integer categoryId = product.getCategoryId();
            while (categoryId != null && visited.add(categoryId)) {
                com.fruitude.product.model.ProductRepository.FrontCategoryRow category = categories.get(categoryId);
                if (category == null) break;
                if (containsGift(category.getName())) giftBox = true;
                categoryId = category.getParentId();
            }
            Integer imageId = cheapest.imageId();
            if (imageId == null) imageId = skus.stream().map(SkuView::imageId).filter(Objects::nonNull).findFirst().orElse(null);
            result.add(new ProductView(product.getProductId(), product.getName(), product.getDescription(), cheapest.price(), cheapest.originalPrice(),
                cheapest.skuId(), imageId, giftBox, skus, cheapest.stock(),
                product.getAverageRating()==null?0d:Math.max(0d,Math.min(5d,product.getAverageRating()))));
        }
        return List.copyOf(result);
    }

    // The stock view field is the order quantity limit, not physical stock.
    static int quantityLimit(Integer status,Integer stock,Integer inbound,Integer outbound) {
        return quantityLimit(status,stock,inbound,outbound,10,SkuSupplyService.DEFAULT_YIELD);
    }
    static int quantityLimit(Integer status,Integer stock,Integer inbound,Integer outbound,Integer backorder) {
        return quantityLimit(status,stock,inbound,outbound,backorder,SkuSupplyService.DEFAULT_YIELD);
    }
    static int quantityLimit(Integer status,Integer stock,Integer inbound,Integer outbound,Integer backorder,java.math.BigDecimal yield) {
        return SkuSupplyService.quantityLimit(status==null?-1:status,stock==null?0:stock,inbound==null?0:inbound,
            outbound==null?0:outbound,backorder==null?10:backorder,yield);
    }

    private boolean containsGift(String text) { return text != null && text.contains("禮盒"); }

    @Transactional(readOnly = true)
    public List<LiveSku> getLiveSkus(List<Integer> ids) {
        List<Integer> requested = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (requested.isEmpty()) return List.of();
        Map<Integer, Integer> promoPrices = activePromoPrices(requested);
        var rates=supply==null?Map.<Integer,java.math.BigDecimal>of():supply.yields(requested);
        return repository.findLiveSkus(requested).stream().map(row -> {
            int stock = quantityLimit(row.getSkuStatus(), row.getStock(), row.getInboundQty(), row.getOutboundQty(), row.getMaxBackorderQty(),rates.getOrDefault(row.getSkuId(),SkuSupplyService.DEFAULT_YIELD));
            boolean available = (Integer.valueOf(1).equals(row.getProductStatus()) || (Integer.valueOf(2).equals(row.getProductStatus()) && Integer.valueOf(6).equals(row.getSkuStatus()))) && row.getSkuStatus() != null && Set.of(1,2,3,6).contains(row.getSkuStatus())
                && row.getPrice() != null && row.getPrice() > 0 && stock > 0;
            return new LiveSku(row.getSkuId(), row.getName(), ProductSku.resolveDisplayName(row.getSkuName(), row.getAnotherName()), promoPrices.getOrDefault(row.getSkuId(), row.getPrice()), row.getPrice(), stock, available, row.getSkuStatus());
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
            item.setOriginalPrice(sku.originalPrice());
        }
    }

    // 目前有效的指定商品活動價（規格編號 → 活動價）；沒有 PromoPriceService 或沒有活動就是空的
    private Map<Integer, Integer> activePromoPrices(Collection<Integer> skuIds) {
        if (promoPriceService == null) return Map.of();
        return promoPriceService.findActivePrices(skuIds);
    }

    /** 保留既有活動／商品更新呼叫的介面；商品資料已取消快取，每次請求直接讀取。 */
    public void clearCache() { }

    // Public product reviews

    @Autowired private NamedParameterJdbcTemplate reviewJdbc;
    public ProductService(NamedParameterJdbcTemplate jdbc) { this.reviewJdbc=jdbc; }

    public record Review(String account, int stars, String skuName, String text, LocalDateTime date) {}
    public record StarCount(int stars,long count) {}
    public record ReviewPage(List<Review> reviews, long count, double average, int page, int size, boolean hasMore,
                             long totalCount,List<StarCount> distribution) {
        public static ReviewPage empty() { return new ReviewPage(List.of(),0,0,0,3,false,0,
            List.of(new StarCount(5,0),new StarCount(4,0),new StarCount(3,0),new StarCount(2,0),new StarCount(1,0))); }
    }
    private static final String FROM = " FROM orders_detail d JOIN product_sku s ON s.sku_id=d.sku_id "
        +"JOIN orders o ON o.orders_id=d.orders_id LEFT JOIN member m ON m.member_id=o.member_id "
        +"WHERE s.product_id=:id AND d.comment_status=1 AND d.comment_star BETWEEN 1 AND 5 "
        +"AND d.comment_text IS NOT NULL AND TRIM(d.comment_text)<>'' "
        +"AND EXISTS (SELECT 1 FROM product p JOIN product_sku sale ON sale.product_id=p.product_id "
        +"WHERE p.product_id=s.product_id AND sale.price>0 AND ((p.status=1 AND sale.status IN (1,2,3,6)) OR (p.status=2 AND sale.status=6))) ";
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ReviewPage read(int productId,int page,int size) { return read(productId,page,size,"","latest"); }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ReviewPage read(int productId, int page, int size, String keyword, String sort) {
        if(productId<=0 || page<0 || page>100000 || size<1 || size>20) throw new IllegalArgumentException("評論查詢參數不正確");
        String query=keyword==null?"":keyword.trim();
        if(query.length()>200)throw new IllegalArgumentException("搜尋文字最多200字");
        String order=switch(sort==null?"latest":sort) {
            case "latest" -> "d.comment_date DESC,d.orders_detail_id DESC";
            case "highest" -> "d.comment_star DESC,d.comment_date DESC,d.orders_detail_id DESC";
            case "lowest" -> "d.comment_star ASC,d.comment_date DESC,d.orders_detail_id DESC";
            default -> throw new IllegalArgumentException("評論排序不正確");
        };
        var params=new HashMap<String,Object>();params.put("id",productId);
        String histogram="";for(int star=1;star<=5;star++)histogram+=", COALESCE(SUM(d.comment_star="+star+"),0) AS star"+star;
        var summary=reviewJdbc.queryForMap("SELECT COUNT(*) AS amount, COALESCE(ROUND(AVG(d.comment_star),1),0) AS average"+histogram+FROM,params);
        long total=((Number)summary.get("amount")).longValue();
        double average=((Number)summary.get("average")).doubleValue();
        var distribution=new ArrayList<StarCount>();
        for(int star=5;star>=1;star--)distribution.add(new StarCount(star,((Number)summary.get("star"+star)).longValue()));
        String filtered=FROM;
        if(!query.isEmpty()) {
            params.put("keyword","%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            filtered+="AND d.comment_text LIKE :keyword ESCAPE '!' ";
        }
        long count=query.isEmpty()?total:reviewJdbc.queryForObject("SELECT COUNT(*)"+filtered,params,Long.class);
        long offset=(long)page*size;
        if(offset>=count)return new ReviewPage(List.of(),count,average,page,size,false,total,List.copyOf(distribution));
        params.put("limit",size);params.put("offset",offset);
        var reviews=reviewJdbc.query("SELECT m.member_account,d.comment_star,s.sku_name,s.another_name,d.comment_text,d.comment_date"+filtered
            +"ORDER BY "+order+" LIMIT :limit OFFSET :offset",params,(rs,n)-> {
                var date=rs.getTimestamp("comment_date");
                return new Review(maskAccount(rs.getString("member_account")),rs.getInt("comment_star"),
                    ProductSku.resolveDisplayName(rs.getString("sku_name"),rs.getString("another_name")),rs.getString("comment_text"),date==null?null:date.toLocalDateTime());
            });
        return new ReviewPage(List.copyOf(reviews),count,average,page,size,offset+reviews.size()<count,total,List.copyOf(distribution));
    }
    public static String maskAccount(String account) {
        String clean=account==null?"":account.replaceAll("[^A-Za-z0-9]", "");
        if(clean.isEmpty())return "會員";
        if(clean.length()==1)return "*";
        return clean.charAt(0)+"*".repeat(Math.max(1,clean.length()-2))+clean.charAt(clean.length()-1);
    }

    // Product editor forms and atomic saves
    public ProductService(ProductRepository products, ProductCategoryRepository categories, VendorRepository vendors,
            ProductLifecycleService lifecycle, ProductStatusAccess access) {
        this.repository=products;this.categories=categories;this.vendors=vendors;this.lifecycle=lifecycle;this.access=access;
    }

    public static final int MAX_PRICE = 10_000_000;




    @Autowired private ProductCategoryRepository categories;
    @Autowired private VendorRepository vendors;

    @Autowired private ProductStatusAccess access;

    public record Choice(Integer id, String label, Integer parentId) {
        public Choice(Integer id, String label) { this(id,label,null); }
    }
    public record SkuForm(Integer skuId, String skuName, String anotherName,
            @tools.jackson.databind.annotation.JsonDeserialize(using=ProductPriceDeserializer.class) Integer price, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer stock,
            @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer safetyStock, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer inboundQty, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer outboundQty, Byte status, String revision,
            Integer originalStock, Integer originalInbound, Integer originalOutbound, Boolean restockConfirmed, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer maxBackorderQty) {
        public SkuForm(Integer skuId, String skuName, String anotherName, Integer price, Integer stock,
                Integer safetyStock, Integer inboundQty, Integer outboundQty, Byte status, String revision,
                Integer originalStock, Integer originalInbound, Integer originalOutbound, Integer maxBackorderQty) {
            this(skuId,skuName,anotherName,price,stock,safetyStock,inboundQty,outboundQty,status,revision,
                originalStock,originalInbound,originalOutbound,false,maxBackorderQty);
        }
        public SkuForm(Integer skuId, String skuName, String anotherName, Integer price, Integer stock,
                Integer safetyStock, Integer inboundQty, Integer outboundQty, Byte status, String revision,
                Integer originalStock, Integer originalInbound, Integer originalOutbound, boolean restockConfirmed) {
            this(skuId,skuName,anotherName,price,stock,safetyStock,inboundQty,outboundQty,status,revision,
                originalStock,originalInbound,originalOutbound,restockConfirmed,null);
        }
        public SkuForm(Integer skuId, String skuName, String anotherName, Integer price, Integer stock,
                Integer safetyStock, Integer inboundQty, Integer outboundQty, Byte status, String revision,
                Integer originalStock, Integer originalInbound, Integer originalOutbound) {
            this(skuId,skuName,anotherName,price,stock,safetyStock,inboundQty,outboundQty,status,revision,
                originalStock,originalInbound,originalOutbound,false,null);
        }
    }
    public record ProductForm(Integer productId, String productName, String productDesc, Integer categoryId,
            Integer vendorId, Byte status, String revision) {}
    public record ImageForm(Integer imageId, Integer skuId, Integer sortOrder, String revision) {}
    public record ImageChange(Integer imageId, Integer skuId, String skuName, Integer sortOrder, String revision, Integer fileIndex, Boolean remove) {}
    public record ImageRequest(SaveRequest edit, List<ImageChange> images) {}
    public record EditData(ProductForm product, List<SkuForm> skus, List<Choice> categories, List<Choice> vendors, boolean canRestore, List<ImageForm> images) {}
    public record SaveRequest(ProductForm product, List<SkuForm> skus, boolean productStatusChanged, boolean activateSkus) {}
    public record Saved(Integer productId, Byte status) {}
    public static class Conflict extends IllegalStateException { public Conflict(String message) { super(message); } }
    private static String revision(LocalDateTime value) { return value == null ? null : value.toString(); }
    private static int zero(Integer value) { return value == null ? 0 : value; }

    @Transactional(readOnly=true)
    public EditData load(Integer id) {
        var p=repository.findById(id).orElseThrow(()->new NoSuchElementException("商品不存在"));
        var form=new ProductForm(p.getProductId(),p.getProductName(),p.getProductDesc(),
            p.getProductCategory()==null?null:p.getProductCategory().getProductCategoryId(),
            p.getVendor()==null?null:p.getVendor().getVendorId(),p.getStatus(),revision(p.getUpdatedAt()));
        var skus=p.getProductSkus().stream().map(s->new SkuForm(s.getSkuId(),s.getSkuName(),s.getAnotherName(),s.getPrice(),
            zero(s.getStock()),zero(s.getSafetyStock()),zero(s.getInboundQty()),zero(s.getOutboundQty()),s.getStatus(),revision(s.getUpdatedAt()),
            zero(s.getStock()),zero(s.getInboundQty()),zero(s.getOutboundQty()),false,zero(s.getMaxBackorderQty()))).toList();
        var categoryOptions=categories.findAll().stream().map(c->new Choice(c.getProductCategoryId(),
            c.getCategoryName(),c.getParentCategory()==null?null:c.getParentCategory().getProductCategoryId())).toList();
        var vendorOptions=vendors.findAll().stream().map(v->new Choice(v.getVendorId(),v.getVendorName())).toList();
        var images=p.getProductSkus().stream().flatMap(s->s.getProductImages().stream())
            .map(i->new ImageForm(i.getImageId(),i.getProductSku().getSkuId(),i.getSortOrder(),revision(i.getUpdatedAt()))).toList();
        return new EditData(form,skus,categoryOptions,vendorOptions,access.canRestoreDiscontinued(),images);
    }

    @Transactional
    public Saved save(SaveRequest request) {
        validateEditRequest(request);
        var p=lockProduct(request.product().productId()).orElseThrow(()->new NoSuchElementException("商品不存在"));
        applyEditToLockedProduct(request,p);
        repository.saveAndFlush(p);
        return new Saved(p.getProductId(),p.getStatus());
    }

    private static void validateEditRequest(SaveRequest request) {
        if(request==null || request.product()==null) throw new IllegalArgumentException("缺少商品資料");
        if(request.product().productId()==null) throw new IllegalArgumentException("缺少商品編號");
    }

    // Both transactional entry points reuse this managed product; no second lock, refresh or intermediate flush.
    private void applyEditToLockedProduct(SaveRequest request,Product p) {
        var f=request.product();
        if(!Objects.equals(f.revision(),revision(p.getUpdatedAt()))) throw new Conflict("商品已被其他操作更新，請取消並重新開啟修改。");
        String name=required(f.productName(),100,"商品名稱");
        if(f.productDesc()!=null && f.productDesc().length()>255) throw new IllegalArgumentException("商品描述不可超過255字");
        if(f.categoryId()==null || f.vendorId()==null) throw new IllegalArgumentException("請選擇分類與廠商");
        var category=categories.findById(f.categoryId()).orElseThrow(()->new IllegalArgumentException("分類不存在"));
        var vendor=vendors.findById(f.vendorId()).orElseThrow(()->new IllegalArgumentException("廠商不存在"));
        if(repository.existsByVendor_VendorIdAndProductNameIgnoreCaseAndProductIdNot(f.vendorId(),name,p.getProductId()))
            throw new IllegalArgumentException("此廠商已有相同商品名稱");
        if(request.skus()==null || request.skus().size()>100) throw new IllegalArgumentException("一次最多修改或新增100個規格");
        var usedIds=new HashSet<Integer>();
        var renamedIds=new HashSet<Integer>();
        request.skus().stream().filter(s->s!=null && s.skuId()!=null && s.skuName()!=null).forEach(s->renamedIds.add(s.skuId()));
        var usedNames=new HashSet<String>();
        p.getProductSkus().stream().filter(s->!renamedIds.contains(s.getSkuId())).forEach(s->usedNames.add(s.getSkuName().trim()));
        for(var input:request.skus()) {
            if(input==null) throw new IllegalArgumentException("規格資料不可空白");
            var existing=input.skuId()==null?null:p.getProductSkus().stream().filter(s->s.getSkuId().equals(input.skuId())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("規格不屬於此商品"));
            if(input.skuId()!=null && !usedIds.add(input.skuId())) throw new IllegalArgumentException("規格編號重複");
            // Omitted status preserves the existing state; explicit changes use lifecycle permissions.
            if(existing!=null && (!Objects.equals(input.revision(),revision(existing.getUpdatedAt()))
                || !Objects.equals(input.originalStock(),zero(existing.getStock()))
                || !Objects.equals(input.originalInbound(),zero(existing.getInboundQty()))
                || !Objects.equals(input.originalOutbound(),zero(existing.getOutboundQty()))))
                throw new Conflict("規格或庫存已變更，請取消並重新開啟修改。");
            if(input.price()==null || input.price()<1) throw new IllegalArgumentException("規格價格須為大於0的正整數");
            if(input.price()>MAX_PRICE) throw new IllegalArgumentException("規格價格不可超過10,000,000元");
            if(input.anotherName()!=null && input.anotherName().length()>50) throw new IllegalArgumentException("規格別名不可超過50字");
            String skuName = existing!=null && input.skuName()==null ? existing.getSkuName() : required(input.skuName(),50,"規格名稱");
            if((existing==null || input.skuName()!=null) && !usedNames.add(skuName)) throw new IllegalArgumentException("同個商品的規格名稱不可重複");
            Byte previous=existing==null?null:existing.getStatus();
            Byte requested=input.status()==null ? (existing==null?ProductLifecycleService.SKU_PREPARED:previous) : input.status();
            if(existing==null || !Objects.equals(previous,requested)) {
                if(!List.of((byte)0,(byte)1,(byte)5,(byte)6).contains(requested))
                    throw new IllegalArgumentException("缺貨、即將售完、售完及永久停產由系統判斷，不能手動選取");
                lifecycle.validateSkuChange(previous,requested,Boolean.TRUE.equals(input.restockConfirmed()));
            }
            if(existing==null) {
                existing=new ProductSku();existing.setProduct(p);existing.setSkuName(skuName);existing.setCreatedAt(LocalDateTime.now());existing.setStatus(ProductLifecycleService.SKU_PREPARED);
                existing.setStock(quantity(input.stock(),0,"庫存量"));
                existing.setSafetyStock(quantity(input.safetyStock(),0,"安全庫存量"));
                existing.setInboundQty(quantity(input.inboundQty(),0,"待進貨"));
                existing.setOutboundQty(quantity(input.outboundQty(),0,"待出貨"));
                existing.setMaxBackorderQty(quantity(input.maxBackorderQty(),10,"允許超賣量"));
                p.getProductSkus().add(existing);
            }
            existing.setStatus(requested);
            existing.setSkuName(skuName);
            existing.setAnotherName(input.anotherName()==null?null:input.anotherName().trim());
            // Existing inventory is owned by PSI; quantities above initialize new SKUs only.
            existing.setPrice(input.price());
            existing.setUpdatedAt(LocalDateTime.now());
        }
        p.setProductName(name);p.setProductDesc(f.productDesc());p.setProductCategory(category);p.setVendor(vendor);
        if(request.productStatusChanged()) lifecycle.changeProduct(p,f.status(),request.activateSkus());
        else if(!request.skus().isEmpty()) lifecycle.synchronize(p);
        p.setUpdatedAt(LocalDateTime.now());
    }
    @Transactional
    public Saved saveWithImages(ImageRequest request, List<ProductImage> uploads) {
        if(request==null || request.images()==null) throw new IllegalArgumentException("缺少商品或圖片資料");
        validateEditRequest(request.edit());
        if(request.images().size()>100) throw new IllegalArgumentException("一次最多調整100張圖片");
        var files=uploads==null?List.<ProductImage>of():uploads;
        var ids=new HashSet<Integer>();
        // Request-only checks are cheap and do not need a database lock.
        for(var change:request.images()) {
            if(change==null) throw new IllegalArgumentException("圖片資料不可空白");
            if(change.imageId()!=null && !ids.add(change.imageId())) throw new IllegalArgumentException("圖片編號重複");
            if(!Boolean.TRUE.equals(change.remove())) {
                if(change.sortOrder()==null || change.sortOrder()<0) throw new IllegalArgumentException("圖片排序不可小於0");
                if(change.fileIndex()!=null && (change.fileIndex()<0 || change.fileIndex()>=files.size())) throw new IllegalArgumentException("圖片檔案資料不完整");
                if(change.imageId()==null && change.fileIndex()==null) throw new IllegalArgumentException("請選擇新增圖片");
            }
        }
        var p=lockProduct(request.edit().product().productId()).orElseThrow(()->new NoSuchElementException("商品不存在"));
        var imagesById=new HashMap<Integer,ProductImage>();
        if(!ids.isEmpty()) p.getProductSkus().forEach(sku->sku.getProductImages().forEach(image->imagesById.put(image.getImageId(),image)));
        for(var change:request.images()) {
            if(change.imageId()==null) continue;
            var image=imagesById.get(change.imageId());
            if(image==null) throw new IllegalArgumentException("圖片不屬於此商品");
            if(!Objects.equals(change.skuId(),image.getProductSku().getSkuId())) throw new IllegalArgumentException("圖片不屬於此規格");
            if(!Objects.equals(change.revision(),revision(image.getUpdatedAt()))) throw new Conflict("圖片已被其他操作修改，請重新開啟修改。");
        }
        applyEditToLockedProduct(request.edit(),p);
        for(var change:request.images()) {
            var sku=p.getProductSkus().stream().filter(s->change.skuId()!=null ? change.skuId().equals(s.getSkuId()) : Objects.equals(change.skuName(),s.getSkuName()))
                .findFirst().orElseThrow(()->new IllegalArgumentException("圖片所屬規格不存在"));
            var image=change.imageId()==null ? null : imagesById.get(change.imageId());
            if(Boolean.TRUE.equals(change.remove())) { if(image!=null) sku.getProductImages().remove(image); continue; }
            if(image==null) { image=new ProductImage();image.setProductSku(sku);image.setCreatedAt(LocalDateTime.now());sku.getProductImages().add(image); }
            if(change.fileIndex()!=null) {
                var upload=files.get(change.fileIndex());
                image.setImageData(upload.getImageData());image.setImageName(upload.getImageName());
                image.setImageType(upload.getImageType());image.setFileSize(upload.getFileSize());
            }
            image.setSortOrder(change.sortOrder());image.setUpdatedAt(LocalDateTime.now());
        }
        repository.saveAndFlush(p);
        return new Saved(p.getProductId(),p.getStatus());
    }

    private static int quantity(Integer value,int fallback,String label) {
        if(value==null) return fallback;
        if(value<0) throw new IllegalArgumentException(label+"須為0或正整數");
        return value;
    }
    private static String required(String value,int limit,String label) {
        if(value==null || value.trim().isEmpty() || value.trim().length()>limit)
            throw new IllegalArgumentException(label+"須為1～"+limit+"字");return value.trim();
    }

    // Related product selection

    private static final Set<String> GENERIC=Set.of("商品","規格","測試","禮盒","家庭","分享","鮮果","推薦","一般","頂級","限定","嚴選","精選","新鮮","包裝","組合");
    private static Set<String> keywords(String name) {
        var result=new LinkedHashSet<String>();
        var matcher=Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+").matcher(name==null?"":name.toLowerCase(Locale.ROOT));
        while(matcher.find()) {
            String word=matcher.group();
            if(Character.UnicodeScript.of(word.codePointAt(0))==Character.UnicodeScript.HAN) {
                int[] chars=word.codePoints().toArray();
                for(int i=0;i+1<chars.length;i++) {String key=new String(chars,i,2);if(!GENERIC.contains(key))result.add(key);}
            } else if(word.length()>=2 && !word.matches("[0-9]+"))result.add(word);
        }
        return result;
    }
    private record Match(ProductView product,int score) {}
    public static List<ProductView> recommend(ProductView selected,List<ProductView> catalog,int limit) {
        if(selected==null || limit<=0)return List.of();
        var keys=keywords(selected.name());
        var candidates=new LinkedHashMap<Integer,ProductView>();
        for(var product:catalog)if(!product.productId().equals(selected.productId()))
            candidates.putIfAbsent(product.productId(),product);
        // Related names come first. Remaining sellable products fill unused positions, without duplicates.
        return candidates.values().stream()
            .map(p->new Match(p,(int)keys.stream().filter(key->p.name()!=null && p.name().toLowerCase(Locale.ROOT).contains(key)).count()))
            .sorted(Comparator.comparingInt(Match::score).reversed()
                .thenComparing(m->m.product().giftBox()!=selected.giftBox())
                .thenComparing(m->m.product().productId()))
            .limit(Math.min(5,limit)).map(Match::product).toList();
    }

    public static class ProductPriceDeserializer extends ValueDeserializer<Integer> {

    @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if(!parser.hasToken(JsonToken.VALUE_NUMBER_INT) && !parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT))
            return context.reportInputMismatch(Integer.class,"規格價格須為數字");
        try { return parser.getDecimalValue().intValueExact(); }
        catch(ArithmeticException ex) { return context.reportInputMismatch(Integer.class,"規格價格須為正整數，且不可超過10,000,000元"); }
    }

    }

    public static class ProductQuantityDeserializer extends ValueDeserializer<Integer> {

    @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if(parser.hasToken(JsonToken.VALUE_NUMBER_INT) || parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
            try { return parser.getDecimalValue().intValueExact(); }
            catch(ArithmeticException ex) { /* Report the same input error below. */ }
        }
        return context.reportInputMismatch(Integer.class,"數量須為整數且不可超過2,147,483,647");
    }

    }

    public static class ProductUnavailableException extends IllegalArgumentException {

    public ProductUnavailableException(String message) { super(message); }

    }

}
