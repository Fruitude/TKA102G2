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
    private ProductService service(List<List<Integer>> writes, boolean missing) {
        var repo = (ProductRepository) Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),
            new Class<?>[]{ProductRepository.class}, (proxy, method, args) -> {
                @SuppressWarnings("unchecked") var ids = new ArrayList<>((Collection<Integer>) args[0]);
                if (method.getName().equals("countByProductIdIn")) return (long) ids.size() - (missing ? 1 : 0);
                assertEquals("updatePageStatus", method.getName());
                assertEquals(Byte.valueOf((byte)0), args[1]);
                assertNotNull(args[2]);
                writes.add(ids);
                return ids.size();
            });
        var service = new ProductService();
        ReflectionTestUtils.setField(service, "repository", repo);
        return service;
    }

    @Test public void onlyUpdatesSubmittedPageIdsOnce() {
        var writes = new ArrayList<List<Integer>>();
        assertEquals(2, service(writes, false).updatePageStatus(List.of(42,43,42), (byte)0));
        assertEquals(List.of(List.of(42,43)), writes);
    }

    @Test public void rejectsInvalidAndMissingProductsBeforeWriting() {
        var writes = new ArrayList<List<Integer>>();
        var service = service(writes, false);
        for (var ids : List.of(List.<Integer>of(), List.of(0), Collections.nCopies(101, 42))) {
            try { service.updatePageStatus(ids, (byte)0); fail(); } catch (IllegalArgumentException expected) {}
        }
        try { service.updatePageStatus(List.of(42), (byte)2); fail(); } catch (IllegalArgumentException expected) {}
        try { service(writes, true).updatePageStatus(List.of(42,43), (byte)0); fail(); } catch (IllegalArgumentException expected) {}
        assertTrue(writes.isEmpty());
    }

    @Test public void endpointBindsPageIdsAndReturnsErrorsWithoutUpdating() throws Exception {
        var writes = new ArrayList<List<Integer>>();
        var controller = new ProductController();
        ReflectionTestUtils.setField(controller, "productSvc", service(writes,false));
        ReflectionTestUtils.setField(controller, "productCategorySvc", new ProductCategoryService() {
            @Override public List<ProductCategory> getAll() { return List.of(); }
        });
        ReflectionTestUtils.setField(controller, "vendorSvc", new VendorService() {
            @Override public List<VendorVO> getAll() { return List.of(); }
        });
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(post("/product/updatePageStatus").param("productIds","42","43").param("status","0"))
            .andReturn().getResponse();
        assertEquals(200,response.getStatus());
        assertTrue(response.getContentAsString().contains("\"updated\":2"));
        assertEquals(400,mvc.perform(post("/product/updatePageStatus").param("productIds","42").param("status","2"))
            .andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(post("/product/updatePageStatus").param("status","0"))
            .andReturn().getResponse().getStatus());
        assertEquals(List.of(List.of(42,43)), writes);
    }
}
