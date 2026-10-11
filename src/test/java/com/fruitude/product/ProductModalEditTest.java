package com.fruitude.product;
import com.fruitude.product.model.*;
import com.fruitude.vendor.model.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProductModalEditTest {
    private final Product p = new Product();
    private final ProductSku s = new ProductSku();
    private final ProductModalEditService service;
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    public ProductModalEditTest() {
        p.setProductId(10); p.setStatus((byte)0); p.setProductName("Original");
        s.setSkuId(1); s.setProduct(p); s.setSkuName("Canonical"); s.setStatus((byte)1); s.setStock(20); s.setInboundQty(0); s.setOutboundQty(0); p.getProductSkus().add(s);
        var products=proxy(ProductRepository.class,(o,m,a)->switch(m.getName()) {
            case "lockForStatus", "findById" -> Optional.of(p);
            case "existsByVendor_VendorIdAndProductNameIgnoreCaseAndProductIdNot" -> false;
            case "saveAndFlush" -> p;
            default -> throw new UnsupportedOperationException(m.getName());
        });
        var parent=new ProductCategory();parent.setProductCategoryId(9);parent.setCategoryName("大分類");
        var category=new ProductCategory(); category.setProductCategoryId(2);category.setCategoryName("小分類");category.setParentCategory(parent);
        var vendor=new VendorVO(); vendor.setVendorId(3);
        var categories=proxy(ProductCategoryRepository.class,(o,m,a)->m.getName().equals("findAll")?List.of(parent,category):Optional.of(category));
        var vendors=proxy(VendorRepository.class,(o,m,a)->m.getName().equals("findAll")?List.of(vendor):Optional.of(vendor));
        var access=new ProductStatusAccess(null,null){ @Override public boolean canRestoreDiscontinued(){return false;} };
        service=new ProductModalEditService(products,categories,vendors,new ProductLifecycleService(access),access);
    }
    private ProductModalEditService.SkuForm sku(Integer id, byte status, int originalStock) {
        return new ProductModalEditService.SkuForm(id,id==null?"New SKU":"Canonical","Alias",199,20,10,0,0,status,null,originalStock,0,0,10);
    }
    private void save(ProductModalEditService.SkuForm form) {
        service.save(new ProductModalEditService.SaveRequest(new ProductModalEditService.ProductForm(10,"Changed","Description",2,3,(byte)0,null),List.of(form),false,false));
    }
    @Test public void categoryChoicesKeepParentIdsForSearchableNestedMenu() {
        var data=service.load(10);var child=data.categories().stream().filter(c->c.id()==2).findFirst().orElseThrow();
        assertEquals("小分類",child.label());assertEquals(Integer.valueOf(9),child.parentId());
        assertNull(data.categories().stream().filter(c->c.id()==9).findFirst().orElseThrow().parentId());
        assertEquals(Integer.valueOf(3),data.vendors().get(0).id());
    }
    @Test public void omittedReadOnlyFieldsDeserializeAndAliasCanBeSaved() {
        var input = tools.jackson.databind.json.JsonMapper.builder().build().readValue(
            "{\"skuId\":1,\"anotherName\":\"新版別名\",\"price\":199,\"originalStock\":20,\"originalInbound\":0,\"originalOutbound\":0}",
            ProductModalEditService.SkuForm.class);
        assertNull(input.restockConfirmed());
        save(input);
        assertEquals("新版別名", s.getAnotherName());
        assertEquals("Canonical", s.getSkuName());
        assertEquals(Integer.valueOf(20), s.getStock());
    }

    @Test public void editsAliasWithoutReplacingCanonicalNameAndSynchronizesProduct() {
        save(sku(1,(byte)1,20)); assertEquals("Canonical",s.getSkuName()); assertEquals("Alias",s.getAnotherName()); assertEquals(Integer.valueOf(199),s.getPrice()); assertEquals(Byte.valueOf((byte)1),p.getStatus());
    }
    @Test public void changesBothCanonicalNameAndAlias() {
        save(new ProductModalEditService.SkuForm(1,"新規格名稱","新別名",199,null,null,null,null,null,null,20,0,0,(Integer)null));
        assertEquals("新規格名稱",s.getSkuName()); assertEquals("新別名",s.getAnotherName());
    }
    @Test public void rejectsEmptyCanonicalName() {
        try {save(new ProductModalEditService.SkuForm(1,"  ","Alias",199,null,null,null,null,null,null,20,0,0,(Integer)null));fail();}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("規格名稱"));}
    }
    @Test public void rejectsNameAlreadyUsedByAnotherSku() {
        var other=new ProductSku();other.setSkuId(2);other.setSkuName("Other");other.setProduct(p);p.getProductSkus().add(other);
        try {save(new ProductModalEditService.SkuForm(1," Other ","Alias",199,null,null,null,null,null,null,20,0,0,(Integer)null));fail();}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("不可重複"));}
    }

    @Test public void newPreparedSkuDoesNotListProduct() {
        s.setStatus((byte)0); save(sku(null,(byte)5,0)); assertEquals(2,p.getProductSkus().size()); assertEquals(Byte.valueOf((byte)5),p.getProductSkus().get(1).getStatus()); assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }
    @Test public void modalCannotOverwriteInventoryEvenWithSubmittedQuantities() {
        s.setStock(50);s.setSafetyStock(12);s.setInboundQty(8);s.setOutboundQty(3);s.setMaxBackorderQty(7);
        save(new ProductModalEditService.SkuForm(1,"Canonical","Alias",199,999,999,999,999,(byte)1,null,50,8,3,999));
        assertEquals(Integer.valueOf(50),s.getStock());assertEquals(Integer.valueOf(12),s.getSafetyStock());
        assertEquals(Integer.valueOf(8),s.getInboundQty());assertEquals(Integer.valueOf(3),s.getOutboundQty());assertEquals(Integer.valueOf(7),s.getMaxBackorderQty());
        assertEquals(Integer.valueOf(199),s.getPrice());assertEquals("Alias",s.getAnotherName());
    }
    @Test public void newModalSkuUsesInventoryDefaultsAndAcceptsMissingReadOnlyFields() {
        s.setStatus((byte)0);
        save(new ProductModalEditService.SkuForm(null,"New SKU",null,199,null,null,null,null,(byte)5,null,null,null,null,(Integer)null));
        var added=p.getProductSkus().get(1);
        assertEquals(Integer.valueOf(0),added.getStock());assertEquals(Integer.valueOf(0),added.getSafetyStock());
        assertEquals(Integer.valueOf(0),added.getInboundQty());assertEquals(Integer.valueOf(0),added.getOutboundQty());assertEquals(Integer.valueOf(10),added.getMaxBackorderQty());
    }
    @Test public void rejectsSkuFromAnotherProduct() {
        try {save(sku(999,(byte)1,20));fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("不屬於"));}
    }
    @Test public void detectsInventoryChangeEvenWhenTimestampIsUnchanged() {
        try {save(sku(1,(byte)1,19));fail();}catch(ProductModalEditService.Conflict expected){} assertNull(s.getAnotherName());
    }
    @Test public void cannotRestoreRetiredSkuThroughModal() {
        s.setStatus((byte)7); save(sku(1,(byte)1,20)); assertEquals(Byte.valueOf((byte)7),s.getStatus());
    }
    @Test public void cannotReturnExistingSkuToPreparedState() {
        save(sku(1,(byte)5,20)); assertEquals(Byte.valueOf((byte)1),s.getStatus());
    }

    @Test public void soldOutModalCannotRestoreStatusEvenWithConfirmation() {
        s.setStatus((byte)4);save(sku(1,(byte)1,20));assertEquals(Byte.valueOf((byte)4),s.getStatus());
        p.setUpdatedAt(null);s.setUpdatedAt(null);
        var f=sku(1,(byte)1,20);
        save(new ProductModalEditService.SkuForm(f.skuId(),f.skuName(),f.anotherName(),f.price(),f.stock(),f.safetyStock(),f.inboundQty(),f.outboundQty(),f.status(),f.revision(),f.originalStock(),f.originalInbound(),f.originalOutbound(),true));
        assertEquals(Byte.valueOf((byte)4),s.getStatus());assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }
    private ProductImage image(int id) {
        var image=new ProductImage();image.setImageId(id);image.setProductSku(s);image.setSortOrder(0);image.setImageData(new byte[]{1});s.getProductImages().add(image);return image;
    }
    private ProductModalEditService.SaveRequest unchanged() {
        return new ProductModalEditService.SaveRequest(new ProductModalEditService.ProductForm(10,"Original",null,2,3,(byte)0,null),List.of(),false,false);
    }
    @Test public void modalReplacesImagePreservingOwnerAndOriginalCreationTime() {
        var old=image(7);var created=java.time.LocalDateTime.of(2026,1,1,0,0);old.setCreatedAt(created);
        var upload=new ProductImage();upload.setImageData(new byte[]{2,3});upload.setImageName("new.png");upload.setImageType("image/png");upload.setFileSize(2);
        service.saveWithImages(new ProductModalEditService.ImageRequest(unchanged(),List.of(new ProductModalEditService.ImageChange(7,1,null,2,null,0,false))),List.of(upload));
        assertArrayEquals(new byte[]{2,3},old.getImageData());assertEquals("new.png",old.getImageName());assertEquals(Integer.valueOf(2),old.getSortOrder());assertSame(s,old.getProductSku());assertEquals(created,old.getCreatedAt());
    }
    @Test public void modalDeletesImageAndAddsOneToNewPreparedSku() {
        image(7);
        var request=new ProductModalEditService.SaveRequest(unchanged().product(),List.of(sku(null,(byte)5,0)),false,false);
        var upload=new ProductImage();upload.setImageData(new byte[]{2});upload.setImageType("image/png");upload.setFileSize(1);
        var changes=List.of(new ProductModalEditService.ImageChange(7,1,null,null,null,null,true),new ProductModalEditService.ImageChange(null,null,"New SKU",0,null,0,false));
        service.saveWithImages(new ProductModalEditService.ImageRequest(request,changes),List.of(upload));
        assertTrue(s.getProductImages().isEmpty());var added=p.getProductSkus().get(1);assertEquals(1,added.getProductImages().size());assertSame(added,added.getProductImages().get(0).getProductSku());assertEquals(Byte.valueOf((byte)5),added.getStatus());
    }
    @Test public void rejectsImageFromAnotherProductBeforeChangingProduct() {
        try {service.saveWithImages(new ProductModalEditService.ImageRequest(unchanged(),List.of(new ProductModalEditService.ImageChange(999,1,null,0,null,null,true))),List.of());fail();}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("不屬於"));}
        assertNull(p.getUpdatedAt());
    }
    @Test public void rejectsConcurrentImageReplacement() {
        image(7).setUpdatedAt(java.time.LocalDateTime.now());
        try {service.saveWithImages(new ProductModalEditService.ImageRequest(unchanged(),List.of(new ProductModalEditService.ImageChange(7,1,null,0,null,null,true))),List.of());fail();}
        catch(ProductModalEditService.Conflict expected){}
        assertEquals(1,s.getProductImages().size());
    }

    @Test public void modalAcceptsOmittedStatusAndCreatesOnlyPreparedSkus() {
        save(new ProductModalEditService.SkuForm(1,"Canonical","Alias",199,null,null,null,null,null,null,20,0,0,(Integer)null));
        assertEquals(Byte.valueOf((byte)1),s.getStatus());
        p.setUpdatedAt(null);s.setUpdatedAt(null);s.setStatus((byte)0);
        save(sku(null,(byte)7,0));assertEquals(Byte.valueOf((byte)5),p.getProductSkus().get(1).getStatus());
    }
}
