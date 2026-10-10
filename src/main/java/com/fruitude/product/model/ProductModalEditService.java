package com.fruitude.product.model;

import java.time.LocalDateTime;
import java.util.*;
import com.fruitude.vendor.model.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductModalEditService {
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final VendorRepository vendors;
    private final ProductLifecycleService lifecycle;
    private final ProductStatusAccess access;
    public ProductModalEditService(ProductRepository products, ProductCategoryRepository categories,
            VendorRepository vendors, ProductLifecycleService lifecycle, ProductStatusAccess access) {
        this.products=products; this.categories=categories; this.vendors=vendors; this.lifecycle=lifecycle; this.access=access;
    }
    public record Choice(Integer id, String label) {}
    public record SkuForm(Integer skuId, String skuName, String anotherName, Integer price, Integer stock,
            Integer safetyStock, Integer inboundQty, Integer outboundQty, Byte status, String revision,
            Integer originalStock, Integer originalInbound, Integer originalOutbound, Integer maxBackorderQty) {}
    public record ProductForm(Integer productId, String productName, String productDesc, Integer categoryId,
            Integer vendorId, Byte status, String revision) {}
    public record EditData(ProductForm product, List<SkuForm> skus, List<Choice> categories, List<Choice> vendors, boolean canRestore) {}
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
            zero(s.getStock()),zero(s.getInboundQty()),zero(s.getOutboundQty()),zero(s.getMaxBackorderQty()))).toList();
        var categoryOptions=categories.findAll().stream().map(c->new Choice(c.getProductCategoryId(),
            (c.getParentCategory()==null?"":c.getParentCategory().getCategoryName()+" / ")+c.getCategoryName())).toList();
        var vendorOptions=vendors.findAll().stream().map(v->new Choice(v.getVendorId(),v.getVendorName())).toList();
        return new EditData(form,skus,categoryOptions,vendorOptions,access.canRestoreDiscontinued());
    }

    @Transactional
    public Saved save(SaveRequest request) {
        if(request==null || request.product()==null) throw new IllegalArgumentException("缺少商品資料");
        var f=request.product();
        if(f.productId()==null) throw new IllegalArgumentException("缺少商品編號");
        var p=products.lockForStatus(f.productId()).orElseThrow(()->new NoSuchElementException("商品不存在"));
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
        var usedNames=new HashSet<String>();p.getProductSkus().forEach(s->usedNames.add(s.getSkuName()));
        for(var input:request.skus()) {
            if(input==null) throw new IllegalArgumentException("規格資料不可空白");
            var existing=input.skuId()==null?null:p.getProductSkus().stream().filter(s->s.getSkuId().equals(input.skuId())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("規格不屬於此商品"));
            if(input.skuId()!=null && !usedIds.add(input.skuId())) throw new IllegalArgumentException("規格編號重複");
            lifecycle.validateSkuChange(existing==null?null:existing.getStatus(),input.status());
            if(existing!=null && (!Objects.equals(input.revision(),revision(existing.getUpdatedAt()))
                || !Objects.equals(input.originalStock(),zero(existing.getStock()))
                || !Objects.equals(input.originalInbound(),zero(existing.getInboundQty()))
                || !Objects.equals(input.originalOutbound(),zero(existing.getOutboundQty()))))
                throw new Conflict("規格或庫存已變更，請取消並重新開啟修改。");
            if(input.price()==null || input.price()<=0) throw new IllegalArgumentException("規格價格必須大於0");
            nonnegative(input.stock(),"庫存量");nonnegative(input.safetyStock(),"安全庫存量");
            nonnegative(input.inboundQty(),"待進貨");nonnegative(input.outboundQty(),"待出貨");
            // 預購額度：前端沒送（舊版畫面）就沿用原值，新規格預設 10
            Integer maxBackorder=input.maxBackorderQty()!=null?input.maxBackorderQty():existing!=null?existing.getMaxBackorderQty():Integer.valueOf(10);
            nonnegative(maxBackorder,"預購額度");
            if(input.anotherName()!=null && input.anotherName().length()>50) throw new IllegalArgumentException("規格別名不可超過50字");
            if(existing==null) {
                String skuName=required(input.skuName(),50,"規格名稱");
                if(!usedNames.add(skuName)) throw new IllegalArgumentException("此商品已有相同規格名稱");
                existing=new ProductSku();existing.setProduct(p);existing.setSkuName(skuName);existing.setCreatedAt(LocalDateTime.now());
                p.getProductSkus().add(existing);
            }
            existing.setAnotherName(input.anotherName()==null?null:input.anotherName().trim());
            existing.setPrice(input.price());existing.setStock(input.stock());existing.setSafetyStock(input.safetyStock());
            existing.setInboundQty(input.inboundQty());existing.setOutboundQty(input.outboundQty());existing.setStatus(input.status());
            existing.setMaxBackorderQty(maxBackorder);
            existing.setUpdatedAt(LocalDateTime.now());
        }
        p.setProductName(name);p.setProductDesc(f.productDesc());p.setProductCategory(category);p.setVendor(vendor);
        if(request.productStatusChanged()) lifecycle.changeProduct(p,f.status(),request.activateSkus());
        else if(!request.skus().isEmpty()) lifecycle.synchronize(p);
        p.setUpdatedAt(LocalDateTime.now());products.saveAndFlush(p);
        return new Saved(p.getProductId(),p.getStatus());
    }
    private static String required(String value,int limit,String label) {
        if(value==null || value.trim().isEmpty() || value.trim().length()>limit)
            throw new IllegalArgumentException(label+"須為1～"+limit+"字");return value.trim();
    }
    private static void nonnegative(Integer value,String label) {
        if(value==null || value<0) throw new IllegalArgumentException(label+"須為0以上整數");
    }
}
