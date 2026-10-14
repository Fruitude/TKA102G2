package com.fruitude.product.model;

import java.util.*;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import com.fruitude.orders.model.CheckoutItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Front-page catalog, live checkout data, recommendations and daily rankings. */
@Service
@org.springframework.scheduling.annotation.EnableScheduling
public class FrontCatalogService {
    private final ProductRepository repository;
    @Autowired
    public FrontCatalogService(ProductRepository repository, NamedParameterJdbcTemplate jdbc) {
        this.repository=repository;this.reviewJdbc=jdbc;
    }
    public FrontCatalogService(ProductRepository repository) {this(repository,null);}
    public FrontCatalogService(NamedParameterJdbcTemplate jdbc) {this(null,jdbc);}
    public FrontCatalogService() {this(null,null);}

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

    // Front catalog and live SKU reads

    @org.springframework.beans.factory.annotation.Autowired(required=false) private SkuSupplyService supply;


    // 指定商品促銷的活動價查詢（promo 套件）。用選擇性欄位注入，沒有它（例如單元測試）就不套用活動價
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fruitude.promo.model.PromoPriceService promoPriceService;

    // price 是前台實際價格（有活動價就是活動價），originalPrice 是規格原價
    public record SkuView(Integer skuId, String name, Integer price, Integer originalPrice, Integer stock, Integer imageId, List<Integer> imageIds, Integer skuStatus) {}
    public record ProductView(Integer productId, String name, String description, Integer price, Integer originalPrice,
        Integer skuId, Integer imageId, boolean giftBox, List<SkuView> skus, Integer stock, Double averageRating) {}
    public record ProductSupplier(Integer vendorId, String name, String origin, boolean hasLogo, Integer farmerPosition) {
        public ProductSupplier {
            if (origin != null) origin = origin.strip().replaceFirst("^(?:台灣|臺灣)[\s、，,／/：:-]*", "");
        }
    }

    @Transactional(readOnly=true)
    public ProductSupplier getProductSupplier(Integer productId) {
        if (productId == null || repository == null) return null;
        var row = repository.findProductSupplier(productId);
        return row == null ? null : new ProductSupplier(row.getVendorId(), row.getName(), row.getOrigin(),
            Integer.valueOf(1).equals(row.getHasLogo()), row.getFarmerPosition());
    }
    public record LiveSku(Integer skuId, String name, String skuName, Integer price, Integer originalPrice, int stock, boolean available, Integer skuStatus) {}

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProductView> getProducts() { return loadProducts(null); }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProductView> getAllProducts(Integer memberId, Collection<Integer> browserFavoriteIds) {
        return rankCatalog(loadProducts(null), repository.findCatalogPriorities(memberId),
            memberId != null, browserFavoriteIds);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<Integer, List<ProductView>> getProductsByVendorIds(List<Integer> vendorIds) {
        Map<Integer, List<ProductView>> result = new LinkedHashMap<>();
        for (Integer vendorId : vendorIds) result.put(vendorId, new ArrayList<>());
        if (vendorIds.isEmpty()) return result;
        Map<Integer, ProductView> live = new HashMap<>();
        for (ProductView product : loadProducts(null)) live.put(product.productId(), product);
        for (VendorProductRow row : repository.findProductIdsByVendorIds(vendorIds)) {
            ProductView product = live.get(row.getProductId());
            if (product != null) result.get(row.getVendorId()).add(product);
        }
        return result;
    }

    @Transactional(readOnly=true)
    public List<ProductView> rankExistingProducts(List<ProductView> products, Integer memberId,
            Collection<Integer> favoriteProductIds) {
        return rankCatalog(products, repository.findCatalogPriorities(memberId), memberId != null, favoriteProductIds);
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
            SkuView cheapest = skus.stream().filter(s -> s.stock() > 0 && Set.of(1,2,3,6).contains(s.skuStatus())).min(Comparator.comparingInt((SkuView s) -> s.originalPrice() != null && s.price() < s.originalPrice() ? 0 : 1).thenComparing(SkuView::price).thenComparing(SkuView::skuId)).orElse(null);
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

    public record SearchQuery(String q,Integer category,String origin,Integer vendor,String promotion,boolean highRated) implements java.io.Serializable {
        public SearchQuery {
            q=q==null?"":q.strip();origin=origin==null?"":origin.strip();promotion=promotion==null?"":promotion.strip();
            if(q.length()>20 || origin.length()>100 || (category!=null&&category<1) || (vendor!=null&&vendor<1)
                    || (!promotion.isEmpty()&&!promotion.equals("any")&&!promotion.matches("[1-9][0-9]{0,8}"))) throw new IllegalArgumentException("搜尋條件不正確");
        }
        public static SearchQuery empty(){return new SearchQuery("",null,"",null,"",false);}
    }
    public record SearchOption(String value,String label,String parent) { public SearchOption(String value,String label){this(value,label,"");} }
    public record SearchMeta(Integer productId,Integer categoryId,Integer vendorId,String vendorName,String origin,double rating) {}
    public record SearchPromotion(Integer productId,Integer promotionId,String title) {}
    public record SearchData(List<ProductView> products,List<SearchMeta> metadata,
            List<ProductRepository.FrontCategoryRow> categories,List<SearchPromotion> promotions) {}
    public record SearchOptions(List<SearchOption> categories,List<SearchOption> origins,List<SearchOption> vendors,List<SearchOption> promotions) {}
    public record SearchResult(List<ProductView> products,int total,int page,int pages,SearchOptions options,List<String> conditions) {}

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SearchData searchData() {
        var products=loadProducts(null);var categories=repository.findFrontCategories();
        if(products.isEmpty()||reviewJdbc==null)return new SearchData(products,List.of(),categories,List.of());
        var ids=products.stream().map(ProductView::productId).toList();
        var params=new HashMap<String,Object>();params.put("ids",ids);params.put("now",LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei")));
        var metadata=reviewJdbc.query("""
            SELECT p.product_id,p.product_category_id,p.vendor_id,COALESCE(v.vendor_name,'') vendor_name,
              COALESCE(v.origin,'') origin,COALESCE(r.rating,0) rating
            FROM product p LEFT JOIN vendor v ON v.vendor_id=p.vendor_id
            LEFT JOIN (SELECT s.product_id,AVG(d.comment_star) rating FROM orders_detail d
              JOIN product_sku s ON s.sku_id=d.sku_id WHERE d.comment_status=1 AND d.comment_star BETWEEN 1 AND 5
              AND d.comment_text IS NOT NULL AND TRIM(d.comment_text)<>'' GROUP BY s.product_id) r ON r.product_id=p.product_id
            WHERE p.product_id IN (:ids)
            """,params,(r,n)->new SearchMeta(r.getInt("product_id"),r.getObject("product_category_id",Integer.class),r.getObject("vendor_id",Integer.class),r.getString("vendor_name"),r.getString("origin").strip(),r.getDouble("rating")));
        params.put("searchSkuIds",products.stream().flatMap(p->p.skus().stream()).filter(s->s.stock()>0 && Set.of(1,2,3,6).contains(s.skuStatus())).map(SkuView::skuId).distinct().toList());
        var promotions=reviewJdbc.query("""
            SELECT DISTINCT s.product_id,pj.promo_project_id,pj.promo_project_title
            FROM promotion pm JOIN promo_project pj ON pj.promo_project_id=pm.promo_project_id
            JOIN product_sku s ON s.sku_id=pm.sku_id
            WHERE s.sku_id IN (:searchSkuIds) AND s.status IN (1,2,3,6) AND s.price>0
              AND pj.promo_type='SKU' AND pj.status=1 AND pj.promo_project_start<=:now AND pj.promo_project_end>=:now
              AND pm.promo_price>0 AND pm.promo_price<s.price
            ORDER BY pj.promo_project_id,s.product_id
            """,params,(r,n)->new SearchPromotion(r.getInt(1),r.getInt(2),r.getString(3)));
        return new SearchData(products,metadata,categories,promotions);
    }
    private static String searchOriginLabel(String origin) {
        String label=origin.strip().replaceFirst("^(?:台灣|臺灣)[\\s、，,／/：:-]*", "");
        return label.isBlank()?"全台":label;
    }
    private static int searchOriginOrder(String origin) {
        String label=searchOriginLabel(origin).replace('臺','台');
        // 西部由北到南，接著東部由北到南，最後離島；其他產地排在後面。
        var counties=List.of("基隆","台北","新北","桃園","新竹","苗栗","台中","彰化","南投","雲林","嘉義","台南","高雄","屏東",
            "宜蘭","花蓮","台東","澎湖","金門","連江","馬祖");
        for(int i=0;i<counties.size();i++)if(label.startsWith(counties.get(i)))return i;
        if(origin.strip().startsWith("台灣")||origin.strip().startsWith("臺灣")||label.equals("全台"))return counties.size();
        return counties.size()+1;
    }

    private static String searchText(String text) {
        return java.text.Normalizer.normalize(text==null?"":text,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('臺','台');
    }
    private static Set<Integer> categoryPath(Integer id,Map<Integer,ProductRepository.FrontCategoryRow> categories) {
        var path=new LinkedHashSet<Integer>();while(id!=null&&path.add(id)){var row=categories.get(id);id=row==null?null:row.getParentId();}return path;
    }
    public static SearchResult search(SearchData data,SearchQuery query,int page) {
        if(page<0||page>100000)throw new IllegalArgumentException("搜尋頁碼不正確");
        var categoryMap=new HashMap<Integer,ProductRepository.FrontCategoryRow>();data.categories().forEach(c->categoryMap.put(c.getCategoryId(),c));
        var meta=new HashMap<Integer,SearchMeta>();data.metadata().forEach(m->meta.put(m.productId(),m));
        var categoryIds=new HashSet<Integer>();data.metadata().forEach(m->categoryIds.addAll(categoryPath(m.categoryId(),categoryMap)));
        var categoryOptions=data.categories().stream().filter(c->categoryIds.contains(c.getCategoryId())).map(c->{
            var names=categoryPath(c.getCategoryId(),categoryMap).stream().map(categoryMap::get).filter(Objects::nonNull).map(ProductRepository.FrontCategoryRow::getName).toList();
            var label=new ArrayList<>(names);Collections.reverse(label);return new SearchOption(c.getCategoryId().toString(),String.join(" › ",label),c.getParentId()==null?"":c.getParentId().toString());
        }).sorted(Comparator.comparing(SearchOption::label)).toList();
        var originOptions=data.metadata().stream().map(SearchMeta::origin).filter(s->!s.isBlank()).distinct().sorted(Comparator.comparingInt(FrontCatalogService::searchOriginOrder).thenComparing(FrontCatalogService::searchOriginLabel).thenComparing(s->s)).map(s->new SearchOption(s,searchOriginLabel(s))).toList();
        var vendors=new TreeMap<Integer,String>();data.metadata().forEach(m->{if(m.vendorId()!=null&&!m.vendorName().isBlank())vendors.put(m.vendorId(),m.vendorName());});
        var vendorOptions=vendors.entrySet().stream().map(e->new SearchOption(e.getKey().toString(),e.getValue())).toList();
        var promos=new TreeMap<Integer,String>();data.promotions().forEach(p->promos.put(p.promotionId(),p.title()));
        var promoOptions=promos.entrySet().stream().map(e->new SearchOption(e.getKey().toString(),e.getValue())).toList();
        var options=new SearchOptions(categoryOptions,originOptions,vendorOptions,promoOptions);
        String text=searchText(query.q());var words=text.isBlank()?List.<String>of():List.of(text.split("\\s+"));
        var matches=data.products().stream().filter(p->{
            var m=meta.get(p.productId());
            if(query.category()!=null&&(m==null||!categoryPath(m.categoryId(),categoryMap).contains(query.category())))return false;
            if(!query.origin().isBlank()&&(m==null||!query.origin().equals(m.origin())))return false;
            if(query.vendor()!=null&&(m==null||!query.vendor().equals(m.vendorId())))return false;
            if(query.highRated()&&(m==null||m.rating()<4||m.rating()>5))return false;
            if(!query.promotion().isBlank()&&data.promotions().stream().noneMatch(promo->promo.productId().equals(p.productId())&&(query.promotion().equals("any")||query.promotion().equals(promo.promotionId().toString()))))return false;
            String haystack=searchText(p.name()+" "+p.description()+" "+p.skus().stream().map(SkuView::name).collect(java.util.stream.Collectors.joining(" "))
                +(m==null?"":" "+m.vendorName()+" "+m.origin())+(m==null?"":" "+categoryPath(m.categoryId(),categoryMap).stream().map(categoryMap::get).filter(Objects::nonNull).map(ProductRepository.FrontCategoryRow::getName).collect(java.util.stream.Collectors.joining(" "))));
            return words.stream().allMatch(haystack::contains);
        }).sorted(Comparator.comparingInt((ProductView p)->text.isBlank()||searchText(p.name()).contains(text)?0:1).thenComparing(ProductView::productId)).toList();
        int pages=Math.max(1,(matches.size()+19)/20),current=Math.min(page,pages-1),start=Math.min(matches.size(),current*20);
        var conditions=new ArrayList<String>();if(!query.q().isBlank())conditions.add("關鍵字："+query.q());
        if(query.category()!=null)conditions.add("水果種類："+optionLabel(categoryOptions,query.category().toString()));
        if(!query.origin().isBlank())conditions.add("產地："+searchOriginLabel(query.origin()));
        if(query.vendor()!=null)conditions.add("供應商："+optionLabel(vendorOptions,query.vendor().toString()));
        if(!query.promotion().isBlank())conditions.add("優惠活動："+(query.promotion().equals("any")?"所有商品優惠":optionLabel(promoOptions,query.promotion())));
        if(query.highRated())conditions.add("評分：4～5 分");
        return new SearchResult(matches.subList(start,Math.min(start+20,matches.size())),matches.size(),current,pages,options,List.copyOf(conditions));
    }
    private static String optionLabel(List<SearchOption> options,String value){return options.stream().filter(o->o.value().equals(value)).map(SearchOption::label).findFirst().orElse("已失效的條件");}


    // Public product reviews

    private final NamedParameterJdbcTemplate reviewJdbc;

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
        return read(productId,page,size,keyword,sort,null);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ReviewPage read(int productId, int page, int size, String keyword, String sort, Integer skuId) {
        if(productId<=0 || page<0 || page>100000 || size<1 || size>20) throw new IllegalArgumentException("評論查詢參數不正確");
        if(skuId!=null && skuId<=0)throw new IllegalArgumentException("評論規格不正確");
        String query=keyword==null?"":keyword.trim();
        if(query.length()>200)throw new IllegalArgumentException("搜尋文字最多200字");
        String order=switch(sort==null?"latest":sort) {
            case "latest" -> "d.comment_date DESC,d.orders_detail_id DESC";
            case "highest" -> "d.comment_star DESC,d.comment_date DESC,d.orders_detail_id DESC";
            case "lowest" -> "d.comment_star ASC,d.comment_date DESC,d.orders_detail_id DESC";
            default -> throw new IllegalArgumentException("評論排序不正確");
        };
        var params=new HashMap<String,Object>();params.put("id",productId);
        String scope=FROM;
        if(skuId!=null) {
            params.put("sku",skuId);
            Integer available=reviewJdbc.queryForObject("SELECT COUNT(*) FROM product_sku s JOIN product p ON p.product_id=s.product_id "
                +"WHERE s.product_id=:id AND s.sku_id=:sku AND s.status IN(1,2,3,4,6) AND s.price>0 "
                +"AND (p.status=1 OR (p.status=2 AND s.status=6))",params,Integer.class);
            if(available==null || available!=1)throw new IllegalArgumentException("此規格不屬於商品或已下架、停產");
            scope+="AND s.sku_id=:sku ";
        }
        String histogram="";for(int star=1;star<=5;star++)histogram+=", COALESCE(SUM(d.comment_star="+star+"),0) AS star"+star;
        var summary=reviewJdbc.queryForMap("SELECT COUNT(*) AS amount, COALESCE(ROUND(AVG(d.comment_star),1),0) AS average"+histogram+scope,params);
        long total=((Number)summary.get("amount")).longValue();
        double average=((Number)summary.get("average")).doubleValue();
        var distribution=new ArrayList<StarCount>();
        for(int star=5;star>=1;star--)distribution.add(new StarCount(star,((Number)summary.get("star"+star)).longValue()));
        String filtered=scope;
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

    public static class ProductUnavailableException extends IllegalArgumentException {

    public ProductUnavailableException(String message) { super(message); }

    }
}
