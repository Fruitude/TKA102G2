package com.fruitude.product;
import com.fruitude.product.model.*;
import com.fruitude.product.controller.ProductSkuController;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.Assert.*;
public class ProductSkuStatusTest {
    private ProductSkuService service(Product product) {
        var sku=product.getProductSkus().get(0);
        var repo=(ProductSkuRepository)Proxy.newProxyInstance(ProductSkuRepository.class.getClassLoader(),new Class<?>[]{ProductSkuRepository.class},(p,m,a)->Optional.of(sku));
        var products=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(p,m,a)->m.getName().equals("lockForStatus")?Optional.of(product):product);
        var svc=new ProductSkuService();ReflectionTestUtils.setField(svc,"repository",repo);ReflectionTestUtils.setField(svc,"products",products);
        ReflectionTestUtils.setField(svc,"lifecycle",new ProductLifecycleService(new ProductStatusAccess(null,null){@Override public boolean canRestoreDiscontinued(){return false;}}));return svc;
    }
    private Product product(int state) {
        var p=new Product();p.setProductId(1);p.setStatus((byte)0);
        var s=new ProductSku();s.setSkuId(2);s.setProduct(p);s.setStatus((byte)state);s.setStock(50);s.setSafetyStock(10);s.setInboundQty(20);s.setOutboundQty(5);s.setSkuName("unchanged");p.getProductSkus().add(s);return p;
    }
    @Test public void statusOnlyUpdatePreservesFieldsAndReturnsActualParentAndSkuStates() {
        var p=product(5);var svc=service(p);var result=svc.updateStatus(2,(byte)2);
        assertEquals(Byte.valueOf((byte)1),result.productStatus());assertEquals(Byte.valueOf((byte)2),result.skuStatuses().get(2));
        var s=p.getProductSkus().get(0);assertEquals("unchanged",s.getSkuName());assertEquals(Integer.valueOf(50),s.getStock());assertEquals(Integer.valueOf(20),s.getInboundQty());assertEquals(Integer.valueOf(5),s.getOutboundQty());
        s.setStock(0);s.setInboundQty(0);s.setOutboundQty(0);result=svc.updateStatus(2,(byte)3);
        assertEquals(Byte.valueOf((byte)0),result.skuStatuses().get(2));assertEquals(Byte.valueOf((byte)0),result.productStatus());
    }
    @Test public void endpointRejectsRestoreAndInvalidStatusAndCanSetRetirement() throws Exception {
        var p=product(4);var c=new ProductSkuController();ReflectionTestUtils.setField(c,"productSkuSvc",service(p));
        var mvc=MockMvcBuilders.standaloneSetup(c).build();
        assertEquals(403,mvc.perform(post("/productsku/updateStatus").param("skuId","2").param("status","1")).andReturn().getResponse().getStatus());
        assertEquals(Byte.valueOf((byte)4),p.getProductSkus().get(0).getStatus());
        assertEquals(400,mvc.perform(post("/productsku/updateStatus").param("skuId","2").param("status","6")).andReturn().getResponse().getStatus());
        p.getProductSkus().get(0).setStatus((byte)1);
        var res=mvc.perform(post("/productsku/updateStatus").param("skuId","2").param("status","4")).andReturn().getResponse();
        assertEquals(200,res.getStatus());assertTrue(res.getContentAsString().contains("\"2\":4"));
    }
}
