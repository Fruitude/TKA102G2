package com.fruitude.product.model;

import java.time.LocalDateTime;
import java.util.*;
import com.fruitude.vendor.model.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductModalEditService {
    @org.springframework.beans.factory.annotation.Autowired
    private ProductInventoryLockService inventoryLocks;
    private java.util.Optional<Product> lockProduct(Integer id) {
        return inventoryLocks==null ? products.lockForStatus(id) : inventoryLocks.lockProduct(id);
    }

    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final VendorRepository vendors;
    private final ProductLifecycleService lifecycle;
    private final ProductStatusAccess access;
    public ProductModalEditService(ProductRepository products, ProductCategoryRepository categories,
            VendorRepository vendors, ProductLifecycleService lifecycle, ProductStatusAccess access) {
        this.products=products; this.categories=categories; this.vendors=vendors; this.lifecycle=lifecycle; this.access=access;
    }
    public record Choice(Integer id, String label, Integer parentId) {
        public Choice(Integer id, String label) { this(id,label,null); }
    }
    public record SkuForm(Integer skuId, String skuName, String anotherName, Integer price, Integer stock,
            Integer safetyStock, Integer inboundQty, Integer outboundQty, Byte status, String revision,
            Integer originalStock, Integer originalInbound, Integer originalOutbound, Boolean restockConfirmed, Integer maxBackorderQty) {
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
        var p=products.findById(id).orElseThrow(()->new NoSuchElementException("商品不存在"));
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
        products.saveAndFlush(p);
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
        if(products.existsByVendor_VendorIdAndProductNameIgnoreCaseAndProductIdNot(f.vendorId(),name,p.getProductId()))
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
            // SKU status is read-only in this editor; existing state is preserved.
            if(existing!=null && (!Objects.equals(input.revision(),revision(existing.getUpdatedAt()))
                || !Objects.equals(input.originalStock(),zero(existing.getStock()))
                || !Objects.equals(input.originalInbound(),zero(existing.getInboundQty()))
                || !Objects.equals(input.originalOutbound(),zero(existing.getOutboundQty()))))
                throw new Conflict("規格或庫存已變更，請取消並重新開啟修改。");
            if(input.price()==null || input.price()<=0) throw new IllegalArgumentException("規格價格必須大於0");
            if(input.anotherName()!=null && input.anotherName().length()>50) throw new IllegalArgumentException("規格別名不可超過50字");
            String skuName = existing!=null && input.skuName()==null ? existing.getSkuName() : required(input.skuName(),50,"規格名稱");
            if((existing==null || input.skuName()!=null) && !usedNames.add(skuName)) throw new IllegalArgumentException("同個商品的規格名稱不可重複");
            if(existing==null) {
                existing=new ProductSku();existing.setProduct(p);existing.setSkuName(skuName);existing.setCreatedAt(LocalDateTime.now());existing.setStatus(ProductLifecycleService.SKU_PREPARED);
                p.getProductSkus().add(existing);
            }
            existing.setSkuName(skuName);
            existing.setAnotherName(input.anotherName()==null?null:input.anotherName().trim());
            // Inventory is owned by PSI. Ignore submitted quantities, preserve current values,
            // and use entity defaults for new SKUs.
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
        products.saveAndFlush(p);
        return new Saved(p.getProductId(),p.getStatus());
    }

    private static String required(String value,int limit,String label) {
        if(value==null || value.trim().isEmpty() || value.trim().length()>limit)
            throw new IllegalArgumentException(label+"須為1～"+limit+"字");return value.trim();
    }
}
