package com.fruitude.product;

import com.fruitude.product.controller.ProductController;
import com.fruitude.product.model.*;
import com.fruitude.vendor.model.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class ProductPageStatusTest {
    private Product p(int id,int state,int skuState) {
        var p=new Product(); p.setProductId(id); p.setProductName("P"+id); p.setStatus((byte)state);
        var sku=new ProductSku();sku.setStatus((byte)skuState);sku.setStock(20);sku.setOutboundQty(0);sku.setProduct(p);p.getProductSkus().add(sku);return p;
    }
    private ProductService service(Map<Integer,Product> data,List<Integer> locks) {
        var repo=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,m,args)->{
            if(m.getName().equals("lockForStatus")){locks.add((Integer)args[0]);return Optional.ofNullable(data.get(args[0]));}
            if(m.getName().equals("flush"))return null;
            if(m.getName().equals("saveAndFlush"))return args[0];
            throw new AssertionError(m.getName());
        });
        var service=new ProductService();ReflectionTestUtils.setField(service,"repository",repo);
        ReflectionTestUtils.setField(service,"lifecycle",new ProductLifecycleService(new ProductStatusAccess(null,null){@Override public boolean canRestoreDiscontinued(){return false;}}));return service;
    }
    @Test public void onlyChangesSubmittedPageAndSkipsPermanentProducts() {
        var first=p(42,1,1);var retired=p(43,2,4);var other=p(44,1,1);var locks=new ArrayList<Integer>();
        var service=service(Map.of(42,first,43,retired,44,other),locks);
        assertEquals(1,service.updatePageStatus(List.of(43,42,42),(byte)0));assertEquals(List.of(42,43),locks);
        assertEquals(Byte.valueOf((byte)0),first.getProductSkus().get(0).getStatus());
        assertEquals(Byte.valueOf((byte)2),retired.getStatus());assertEquals(Byte.valueOf((byte)4),retired.getProductSkus().get(0).getStatus());assertEquals(Byte.valueOf((byte)1),other.getStatus());
    }
    @Test public void rejectsInvalidOrMissingPageBeforeWriting() {
        var first=p(42,1,1);var service=service(Map.of(42,first),new ArrayList<>());
        for(var ids:List.of(List.<Integer>of(),List.of(0),Collections.nCopies(101,42),List.of(42,43))){try{service.updatePageStatus(ids,(byte)0);fail();}catch(IllegalArgumentException expected){}}
        try{service.updatePageStatus(List.of(42),(byte)2);fail();}catch(IllegalArgumentException expected){}
        assertEquals(Byte.valueOf((byte)1),first.getStatus());
    }
    @Test public void pageListingPreflightsConfirmationBeforeAnyChange() {
        var first=p(42,0,1);var second=p(43,0,0);var service=service(Map.of(42,first,43,second),new ArrayList<>());
        try{service.updatePageStatus(List.of(42,43),(byte)1);fail();}catch(ProductStatusConfirmationException expected){}
        assertEquals(Byte.valueOf((byte)0),first.getStatus());assertEquals(Byte.valueOf((byte)0),second.getStatus());
        assertEquals(2,service.updatePageStatus(List.of(42,43),(byte)1,true));assertEquals(Byte.valueOf((byte)1),second.getProductSkus().get(0).getStatus());
    }
    @Test public void endpointsRequireConfirmationAndDenyUnauthorizedRestore() throws Exception {
        var controller=new ProductController();ReflectionTestUtils.setField(controller,"productSvc",service(Map.of(42,p(42,0,0),43,p(43,2,4)),new ArrayList<>()));
        ReflectionTestUtils.setField(controller,"productCategorySvc",new ProductCategoryService(){@Override public List<ProductCategory> getAll(){return List.of();}});
        ReflectionTestUtils.setField(controller,"vendorSvc",new VendorService(){@Override public List<VendorVO> getAll(){return List.of();}});
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(409,mvc.perform(post("/product/updateStatus").param("productId","42").param("status","1")).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(post("/product/updateStatus").param("productId","42").param("status","1").param("activateSkus","true")).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/product/updateStatus").param("productId","43").param("status","0")).andReturn().getResponse().getStatus());
        var response=mvc.perform(post("/product/updatePageStatus").param("productIds","42","43").param("status","0")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("\"updated\":1"));assertTrue(response.getContentAsString().contains("\"skipped\":1"));
        assertEquals(400,mvc.perform(post("/product/updatePageStatus").param("productIds","42").param("status","2")).andReturn().getResponse().getStatus());
    }
    @Test public void preparedPageListsWithoutConfirmationAndSkipsRetiredRows() {
        var prepared=p(42,0,6);var retired=p(43,2,4);var service=service(Map.of(42,prepared,43,retired),new ArrayList<>());
        assertEquals(1,service.updatePageStatus(List.of(42,43),(byte)1));
        assertEquals(Byte.valueOf((byte)1),prepared.getProductSkus().get(0).getStatus());
        assertEquals(Byte.valueOf((byte)2),retired.getStatus());
    }
}
