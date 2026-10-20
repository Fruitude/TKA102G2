package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import com.fruitude.vendor.model.VendorRepository;
import tools.jackson.core.*;
import tools.jackson.databind.*;

@Service
public class ProductService {
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

    public static LocalDateTime earliestListingTime() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Taipei")).plusDays(1).atStartOfDay();
    }
    public static LocalDateTime latestListingTime() {
        return earliestListingTime().toLocalDate().minusDays(1).plusYears(1).atTime(23,59);
    }
    public static String listingTimeError(ProductSku sku,LocalDateTime earliest) {
        return listingTimeError(sku.getStatus(),sku.getDefaultListingTime(),earliest);
    }
    private static String listingTimeError(Byte status,LocalDateTime time,LocalDateTime earliest) {
        if(!Byte.valueOf((byte)5).equals(status))return null;
        if(time==null)return "請選擇預計上架時間";
        if(time.isBefore(earliest))return "預計上架時間最早為隔天 00:00（臺灣時間）";
        var latest=earliest.toLocalDate().minusDays(1).plusYears(1).atTime(23,59);
        if(time.isAfter(latest))return "預計上架時間不可超過一年後同日 23:59（臺灣時間）";
        return null;
    }

    @org.springframework.transaction.annotation.Transactional
    public void addProduct(Product product, boolean activateSkus) {
        var earliest=earliestListingTime();
        var prepared=new ArrayList<ProductSku>();
        for(var sku:product.getProductSkus()) {
            var error=listingTimeError(sku,earliest);if(error!=null)throw new IllegalArgumentException(error);
            if(Byte.valueOf((byte)5).equals(sku.getStatus()))prepared.add(sku);
            else sku.setDefaultListingTime(null);
        }
        if(!prepared.isEmpty()) {
            if(Byte.valueOf((byte)2).equals(product.getStatus()))throw new IllegalArgumentException("永久停產商品不可設定預備上架");
            // Listing the parent must not activate newly scheduled SKUs ahead of their chosen time.
            prepared.forEach(sku->sku.setStatus((byte)0));
            try {
                lifecycle.closeDepletedSkus(product);
                boolean hasSellable=product.getProductSkus().stream().anyMatch(sku->ProductLifecycleService.isSellable(sku.getStatus()));
                product.setStatus(hasSellable?(byte)1:(byte)0);
                lifecycle.changeProduct(product,product.getStatus(),activateSkus);
            } finally { prepared.forEach(sku->sku.setStatus((byte)5)); }
            lifecycle.synchronize(product);
            repository.saveAndFlush(product);
            return;
        }
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

    @Transactional
    public void deleteProduct(Integer productId) {
        if (productId == null || productId <= 0) throw new IllegalArgumentException("商品不存在");
        var product = repository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("商品不存在，可能已被刪除"));
        // Keep cascading image/SKU removal and its flush in one transaction. A failed
        // constraint must roll back the whole deletion before the controller redirects.
        repository.delete(product);
        repository.flush();
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
        return getOverviewPage(page,size,statusFilter,parentCategoryId,categoryId,vendorId,stockFilter,
            minComments,maxComments,ratingFilter,sortBy,sortDirection,keyword,searchProductName,searchSkuName,"");
    }

    public org.springframework.data.domain.Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String statusFilter,
            Integer parentCategoryId, Integer categoryId, Integer vendorId, String stockFilter,
            Integer minComments, Integer maxComments, String ratingFilter, String sortBy, String sortDirection,
            String keyword, boolean searchProductName, boolean searchSkuName, String origin) {
        origin = origin == null ? "" : origin.strip();
        if (origin.length() > 100) throw new IllegalArgumentException("產地條件不正確");
        if (!searchProductName && !searchSkuName) searchProductName = true;
        String normalizedKeyword = normalizeOverviewKeyword(keyword);
        var words = normalizedKeyword.isEmpty() ? java.util.List.<String>of() : java.util.Arrays.stream(normalizedKeyword.toLowerCase(java.util.Locale.ROOT).split(" "))
            .distinct().map(word -> word.replace("!", "!!").replace("%", "!%").replace("_", "!_")).toList();
        String searchPattern;
        try { searchPattern = new tools.jackson.databind.ObjectMapper().writeValueAsString(words); }
        catch (tools.jackson.core.JacksonException error) { throw new IllegalArgumentException("搜尋文字無法處理", error); }
        sortBy = normalizeOverviewSort(sortBy);
        sortDirection = "desc".equals(sortDirection) ? "desc" : "asc";
        stockFilter = normalizeStockFilter(stockFilter);
        ratingFilter = normalizeRatingFilter(ratingFilter);
        Integer ratingBucket = ratingFilter.isEmpty() ? null : "unrated".equals(ratingFilter) ? -1 : ratingFilter.charAt(0) - '0';
        int safeSize = java.util.Set.of(10, 20, 50, 100).contains(size) ? size : 10;
        Integer productStatus = "on".equals(statusFilter) ? Integer.valueOf(1) : "off".equals(statusFilter) ? Integer.valueOf(0) : "discontinued".equals(statusFilter) ? Integer.valueOf(2) : null;
        var result = repository.findOverviewPage(productStatus, parentCategoryId, categoryId, vendorId, stockFilter, minComments, maxComments, ratingBucket, sortBy, sortDirection, searchPattern, searchProductName, searchSkuName, origin,
            org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), safeSize));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            return repository.findOverviewPage(productStatus, parentCategoryId, categoryId, vendorId, stockFilter, minComments, maxComments, ratingBucket, sortBy, sortDirection, searchPattern, searchProductName, searchSkuName, origin,
            org.springframework.data.domain.PageRequest.of(result.getTotalPages() - 1, safeSize));
        }
        return result;
    }

    public java.util.List<FrontCatalogService.SearchOption> getOverviewOrigins() {
        return FrontCatalogService.originOptions(repository.findOverviewOrigins());
    }

    public static String normalizeOverviewKeyword(String keyword) {
        String value = (keyword == null ? "" : keyword).replaceAll("[\\s\\p{Zs}]+", " ").strip();
        if (value.length() <= 30) return value;
        int end = Character.isHighSurrogate(value.charAt(29)) ? 29 : 30;
        return value.substring(0, end).stripTrailing();
    }

    /** Compatibility entry point for the unchanged promotion module; events refresh front cards. */
    public void clearCache() { }

    // Product editor forms and atomic saves
    public ProductService(ProductRepository products, ProductCategoryRepository categories, VendorRepository vendors,
            ProductLifecycleService lifecycle, ProductStatusAccess access) {
        this.repository=products;this.categories=categories;this.vendors=vendors;this.lifecycle=lifecycle;this.access=access;
    }

    public static final int MAX_PRICE = 10_000_000;




    @Autowired private ProductCategoryRepository categories;
    @Autowired private VendorRepository vendors;

    @Autowired private ProductStatusAccess access;

    public record Choice(Integer id, String label, Integer parentId, String origin, boolean hasLogo) {
        public Choice(Integer id, String label, Integer parentId) { this(id,label,parentId,null,false); }
        public Choice(Integer id, String label) { this(id,label,null,null,false); }
    }
    public record SkuForm(Integer skuId, String skuName, String anotherName,
            @tools.jackson.databind.annotation.JsonDeserialize(using=ProductPriceDeserializer.class) Integer price, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer stock,
            @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer safetyStock, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer inboundQty, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer outboundQty, Byte status, String revision,
            Integer originalStock, Integer originalInbound, Integer originalOutbound, Boolean restockConfirmed, @tools.jackson.databind.annotation.JsonDeserialize(using=ProductQuantityDeserializer.class) Integer maxBackorderQty, LocalDateTime defaultListingTime) {
        public SkuForm(Integer skuId, String skuName, String anotherName, Integer price, Integer stock,
                Integer safetyStock, Integer inboundQty, Integer outboundQty, Byte status, String revision,
                Integer originalStock, Integer originalInbound, Integer originalOutbound, Boolean restockConfirmed, Integer maxBackorderQty) {
            this(skuId,skuName,anotherName,price,stock,safetyStock,inboundQty,outboundQty,status,revision,
                originalStock,originalInbound,originalOutbound,restockConfirmed,maxBackorderQty,null);
        }
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
            zero(s.getStock()),zero(s.getInboundQty()),zero(s.getOutboundQty()),false,zero(s.getMaxBackorderQty()),s.getDefaultListingTime())).toList();
        var categoryOptions=categories.findAll().stream().map(c->new Choice(c.getProductCategoryId(),
            c.getCategoryName(),c.getParentCategory()==null?null:c.getParentCategory().getProductCategoryId())).toList();
        var vendorOptions=vendors.findAll().stream().map(v->new Choice(v.getVendorId(),v.getVendorName(),null,v.getOrigin(),v.getLogo()!=null && v.getLogo().length>0)).toList();
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
        p.getProductSkus().stream().filter(s->!renamedIds.contains(s.getSkuId())).forEach(s->usedNames.add(s.getSkuName().trim().toLowerCase(java.util.Locale.ROOT)));
        var newPrepared=new ArrayList<ProductSku>();
        var earliest=earliestListingTime();
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
            if((existing==null || input.skuName()!=null) && !usedNames.add(skuName.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("同個商品的規格名稱不可重複");
            Byte previous=existing==null?null:existing.getStatus();
            Byte requested=input.status()==null ? (existing==null?ProductLifecycleService.SKU_PREPARED:previous) : input.status();
            if(existing==null || !Objects.equals(previous,requested)) {
                if(!List.of((byte)0,(byte)1,(byte)5,(byte)6).contains(requested))
                    throw new IllegalArgumentException("缺貨、即將售完、售完及永久停產由系統判斷，不能手動選取");
                lifecycle.validateSkuChange(previous,requested,Boolean.TRUE.equals(input.restockConfirmed()));
            }
            if(existing==null) {
                String timeError=listingTimeError(requested,input.defaultListingTime(),earliest);
                if(timeError!=null)throw new IllegalArgumentException(timeError);
                if(Byte.valueOf((byte)5).equals(requested) && Byte.valueOf((byte)2).equals(request.productStatusChanged()?f.status():p.getStatus()))
                    throw new IllegalArgumentException("永久停產商品不可設定預備上架");
                existing=new ProductSku();existing.setProduct(p);existing.setSkuName(skuName);existing.setCreatedAt(LocalDateTime.now());existing.setStatus(ProductLifecycleService.SKU_PREPARED);
                existing.setStock(quantity(input.stock(),0,"庫存量"));
                existing.setSafetyStock(quantity(input.safetyStock(),0,"安全庫存量"));
                existing.setInboundQty(quantity(input.inboundQty(),0,"待進貨"));
                existing.setOutboundQty(quantity(input.outboundQty(),0,"待出貨"));
                existing.setMaxBackorderQty(quantity(input.maxBackorderQty(),10,"允許超賣量"));
                existing.setDefaultListingTime(Byte.valueOf((byte)5).equals(requested)?input.defaultListingTime():null);
                if(Byte.valueOf((byte)5).equals(requested))newPrepared.add(existing);
                p.getProductSkus().add(existing);
            }
            if(!Byte.valueOf((byte)5).equals(requested))existing.setDefaultListingTime(null);
            existing.setStatus(requested);
            existing.setSkuName(skuName);
            existing.setAnotherName(input.anotherName()==null?null:input.anotherName().trim());
            // Existing inventory is owned by PSI; quantities above initialize new SKUs only.
            existing.setPrice(input.price());
            existing.setUpdatedAt(LocalDateTime.now());
        }
        p.setProductName(name);p.setProductDesc(f.productDesc());p.setProductCategory(category);p.setVendor(vendor);
        if(request.productStatusChanged() && !newPrepared.isEmpty()) {
            // A simultaneous parent-status change must not activate the newly scheduled SKUs early.
            newPrepared.forEach(sku->sku.setStatus((byte)0));
            try {
                Byte target=f.status();
                if(Byte.valueOf((byte)1).equals(target) && p.getProductSkus().stream().filter(sku->!newPrepared.contains(sku))
                        .noneMatch(sku->ProductLifecycleService.isSellable(sku.getStatus()) || Byte.valueOf((byte)0).equals(sku.getStatus()) || Byte.valueOf((byte)5).equals(sku.getStatus())))target=(byte)0;
                lifecycle.changeProduct(p,target,request.activateSkus());
            } finally { newPrepared.forEach(sku->sku.setStatus((byte)5)); }
            lifecycle.synchronize(p);
        }
        else if(request.productStatusChanged()) lifecycle.changeProduct(p,f.status(),request.activateSkus());
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

}
