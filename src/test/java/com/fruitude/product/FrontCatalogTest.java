package com.fruitude.product;

import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.fruitude.product.model.*;
import com.fruitude.product.controller.FrontProductController;

public class FrontCatalogTest {
    private FrontCatalogService service(List<Product> products) {
        ProductRepository repo = (ProductRepository) Proxy.newProxyInstance(ProductRepository.class.getClassLoader(), new Class<?>[]{ProductRepository.class}, (proxy, method, args) -> {
            assertEquals("findByStatusOrderByProductIdAsc", method.getName()); assertEquals(Byte.valueOf((byte) 1), args[0]); return products;
        });
        return new FrontCatalogService(repo);
    }
    private Product product(int id, String name) { Product p = new Product(); p.setProductId(id); p.setProductName(name); p.setStatus((byte)1); p.setProductDesc("資料庫描述"); return p; }
    private ProductSku sku(Product p, int id, int price, int status) { ProductSku s = new ProductSku(); s.setSkuId(id); s.setSkuName("規格" + id); s.setPrice(price); s.setStock(10); s.setStatus((byte)status); s.setProduct(p); p.getProductSkus().add(s); return s; }
    private void image(ProductSku s, int id, int order) { ProductImage i = new ProductImage(); i.setImageId(id); i.setSortOrder(order); i.setImageData(new byte[]{1}); i.setProductSku(s); s.getProductImages().add(i); }
    private MockMvc mvc(FrontCatalogService service) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver view = new ThymeleafViewResolver(); view.setTemplateEngine(engine); view.setCharacterEncoding("UTF-8");
        return MockMvcBuilders.standaloneSetup(new FrontProductController(service)).setViewResolvers(view).build();
    }
    @Test public void pricesImagesAndVisibilityComeFromActiveSkus() {
        Product p = product(1, "測試芒果"); sku(p, 1, 1, 0); ProductSku available = sku(p, 2, 200, 1); image(available, 9, 2); image(available, 10, 1);
        Product noActiveSku = product(2, "隱藏商品"); sku(noActiveSku, 3, 50, 0);
        List<FrontCatalogService.ProductView> result = service(List.of(p, noActiveSku)).getProducts();
        assertEquals(1, result.size()); assertEquals(Integer.valueOf(200), result.get(0).price()); assertEquals(Integer.valueOf(2), result.get(0).skuId()); assertEquals(Integer.valueOf(10), result.get(0).imageId());
        assertFalse(result.get(0).giftBox());
    }
    @Test public void giftClassificationUsesCategoryNotPrice() {
        Product p = product(1, "便宜水果"); sku(p, 1, 10, 1);
        ProductCategory parent = new ProductCategory(); parent.setProductCategoryId(1); parent.setCategoryName("精選禮盒");
        ProductCategory child = new ProductCategory(); child.setProductCategoryId(2); child.setCategoryName("小盒"); child.setParentCategory(parent); p.setProductCategory(child);
        assertTrue(service(List.of(p)).getProducts().get(0).giftBox());
    }
    @Test public void giftPackagingInSkuNameIsRecognized() {
        Product p = product(1, "愛文芒果"); ProductSku s = sku(p, 1, 799, 1); s.setSkuName("愛文芒果6顆禮盒");
        assertTrue(service(List.of(p)).getProducts().get(0).giftBox());
        s.setStatus((byte)0); sku(p, 2, 100, 1);
        assertFalse(service(List.of(p)).getProducts().get(0).giftBox());
    }
    @Test public void homeRendersDatabaseCardsAndContextAwareLinks() throws Exception {
        Product p = product(5, "資料庫限定芒果"); ProductSku s = sku(p, 42, 345, 1); image(s, 9, 0);
        MockMvc mvc = mvc(service(List.of(p)));
        for (String path : List.of("/front/", "/front/index", "/front/index.html", "/front/all-products/", "/front/all-products/index.html", "/front/category/seasonal-fresh-fruit/")) {
            var response = mvc.perform(get(path)).andReturn().getResponse(); assertEquals(200, response.getStatus());
            String html = response.getContentAsString(); assertTrue(html.contains("資料庫限定芒果")); assertTrue(html.contains("NT$ 345")); assertTrue(html.contains("productId=5")); assertTrue(html.contains("data-commerce-sku-id=\"42\"")); assertTrue(html.contains("/product/image/9")); assertFalse(html.contains("智利櫻桃禮盒"));
        }
        String deployed = mvc.perform(get("/TKA102G2/front/").contextPath("/TKA102G2")).andReturn().getResponse().getContentAsString(); assertTrue(deployed.contains("/TKA102G2/product/image/9"));
    }
    @Test public void giftCategoryAndCanonicalRoutesRender() throws Exception {
        Product p = product(8, "芒果禮盒"); sku(p, 18, 899, 1);
        MockMvc mvc = mvc(service(List.of(p)));
        for (String path : List.of("/front/", "/front/all-products/", "/front/category/featured-gift-boxes/", "/front/category/featured-gift-boxes/index.html")) {
            var response = mvc.perform(get(path)).andReturn().getResponse(); assertEquals(200, response.getStatus()); assertTrue(response.getContentAsString().contains("芒果禮盒"));
            assertFalse(response.getContentAsString().contains("js/product-catalog.js"));
        }
        for (String path : List.of("/front", "/front/all-products", "/front/category/seasonal-fresh-fruit", "/front/category/featured-gift-boxes")) {
            var response = mvc.perform(get(path)).andReturn().getResponse(); assertEquals(302, response.getStatus()); assertEquals(path + "/", response.getRedirectedUrl());
        }
    }
    @Test public void emptyHomeAndMissingDetailsHaveNoFakeFallback() throws Exception {
        MockMvc mvc = mvc(service(List.of()));
        String home = mvc.perform(get("/front/")).andReturn().getResponse().getContentAsString(); assertTrue(home.contains("目前沒有已上架商品")); assertFalse(home.contains("智利櫻桃"));
        String detail = mvc.perform(get("/front/product/view/").param("productId", "999")).andReturn().getResponse().getContentAsString(); assertTrue(detail.contains("找不到這個商品")); assertFalse(detail.contains("id=\"pv-form\""));
    }
    @Test public void detailUsesIdEvenWhenNamesDuplicate() throws Exception {
        Product a = product(1, "同名水果"); sku(a, 10, 100, 1); Product b = product(2, "同名水果"); sku(b, 20, 200, 1);
        String html = mvc(service(List.of(a,b))).perform(get("/front/product/view/").param("productId", "2")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-commerce-sku-id=\"20\"")); assertTrue(html.contains("NT$ 200")); assertFalse(html.contains("../../js/product-catalog.js"));
    }
}
