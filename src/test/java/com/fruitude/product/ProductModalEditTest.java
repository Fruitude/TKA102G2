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
            case "lockForStatus" -> Optional.of(p);
            case "existsByVendor_VendorIdAndProductNameIgnoreCaseAndProductIdNot" -> false;
            case "saveAndFlush" -> p;
            default -> throw new UnsupportedOperationException(m.getName());
        });
        var category=new ProductCategory(); category.setProductCategoryId(2);
        var vendor=new VendorVO(); vendor.setVendorId(3);
        var categories=proxy(ProductCategoryRepository.class,(o,m,a)->Optional.of(category));
        var vendors=proxy(VendorRepository.class,(o,m,a)->Optional.of(vendor));
        var access=new ProductStatusAccess(null,null){ @Override public boolean canRestoreDiscontinued(){return false;} };
        service=new ProductModalEditService(products,categories,vendors,new ProductLifecycleService(access),access);
    }
    private ProductModalEditService.SkuForm sku(Integer id, byte status, int originalStock) {
        return new ProductModalEditService.SkuForm(id,"New SKU","Alias",199,20,10,0,0,status,null,originalStock,0,0,10);
    }
    private void save(ProductModalEditService.SkuForm form) {
        service.save(new ProductModalEditService.SaveRequest(new ProductModalEditService.ProductForm(10,"Changed","Description",2,3,(byte)0,null),List.of(form),false,false));
    }
    @Test public void editsAliasWithoutReplacingCanonicalNameAndSynchronizesProduct() {
        save(sku(1,(byte)1,20)); assertEquals("Canonical",s.getSkuName()); assertEquals("Alias",s.getAnotherName()); assertEquals(Integer.valueOf(199),s.getPrice()); assertEquals(Byte.valueOf((byte)1),p.getStatus());
    }
    @Test public void newPreparedSkuDoesNotListProduct() {
        s.setStatus((byte)0); save(sku(null,(byte)5,0)); assertEquals(2,p.getProductSkus().size()); assertEquals(Byte.valueOf((byte)5),p.getProductSkus().get(1).getStatus()); assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }
    @Test public void rejectsSkuFromAnotherProduct() {
        try {save(sku(999,(byte)1,20));fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("不屬於"));}
    }
    @Test public void detectsInventoryChangeEvenWhenTimestampIsUnchanged() {
        try {save(sku(1,(byte)1,19));fail();}catch(ProductModalEditService.Conflict expected){} assertNull(s.getAnotherName());
    }
    @Test public void cannotRestoreRetiredSkuThroughModal() {
        s.setStatus((byte)7); try {save(sku(1,(byte)1,20));fail();}catch(ProductStatusAccessException expected){} assertEquals(Byte.valueOf((byte)7),s.getStatus());
    }
    @Test public void cannotReturnExistingSkuToPreparedState() {
        try {save(sku(1,(byte)5,20));fail();}catch(IllegalArgumentException expected){} assertEquals(Byte.valueOf((byte)1),s.getStatus());
    }

    @Test public void soldOutModalCannotListUntilRestockWasConfirmed() {
        s.setStatus((byte)4);
        try {save(sku(1,(byte)1,20));fail();}catch(SkuRestockConfirmationException expected){}
        assertEquals(Byte.valueOf((byte)4),s.getStatus());
        var f=sku(1,(byte)1,20);
        save(new ProductModalEditService.SkuForm(f.skuId(),f.skuName(),f.anotherName(),f.price(),f.stock(),f.safetyStock(),f.inboundQty(),f.outboundQty(),f.status(),f.revision(),f.originalStock(),f.originalInbound(),f.originalOutbound(),true));
        assertEquals(Byte.valueOf((byte)1),p.getStatus());
    }
}
