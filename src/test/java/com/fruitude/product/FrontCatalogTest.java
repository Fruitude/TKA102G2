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
            if (method.getName().equals("findFrontRows")) {
                List<FrontCatalogRow> rows = new ArrayList<>();
                for (Product p : products) if (args[0] == null || args[0].equals(p.getProductId()))
                    for (ProductSku s : p.getProductSkus()) if (((s.getStatus() >= 1 && s.getStatus() <= 3) || s.getStatus()==4) && s.getPrice() != null && s.getPrice() > 0)
                        rows.add(projection(FrontCatalogRow.class, key -> switch (key) {
                            case "getProductId" -> p.getProductId(); case "getName" -> p.getProductName(); case "getDescription" -> p.getProductDesc();
                            case "getCategoryId" -> p.getProductCategory() == null ? null : p.getProductCategory().getProductCategoryId();
                            case "getSkuId" -> s.getSkuId(); case "getSkuName" -> s.getSkuName(); case "getAnotherName" -> s.getAnotherName(); case "getPrice" -> s.getPrice(); case "getSkuStatus" -> (int)s.getStatus(); case "getStock" -> s.getStock(); case "getInboundQty" -> s.getInboundQty(); case "getOutboundQty" -> s.getOutboundQty(); case "getMaxBackorderQty" -> s.getMaxBackorderQty(); default -> null;
                        }));
                return rows;
            }
            if (method.getName().equals("findFrontImages")) {
                List<FrontImageRow> rows = new ArrayList<>();
                for (Product p : products) if (((List<?>)args[0]).contains(p.getProductId()))
                    for (ProductSku s : p.getProductSkus()) if (((s.getStatus() >= 1 && s.getStatus() <= 3) || s.getStatus()==4) && s.getPrice() != null && s.getPrice() > 0)
                        for (ProductImage i : s.getProductImages().stream().filter(i -> i.getImageData() != null && i.getImageData().length > 0)
                            .sorted(Comparator.comparing((ProductImage i) -> i.getSortOrder() == null ? 0 : i.getSortOrder()).thenComparing(ProductImage::getImageId)).toList())
                            rows.add(projection(FrontImageRow.class, key -> key.equals("getSkuId") ? s.getSkuId() : i.getImageId()));
                return rows;
            }
            if (method.getName().equals("findFrontCategories")) {
                Map<Integer, FrontCategoryRow> rows = new HashMap<>();
                for (Product p : products) {
                    Set<Integer> visited = new HashSet<>();
                    for (ProductCategory c = p.getProductCategory(); c != null && visited.add(c.getProductCategoryId()); c = c.getParentCategory()) {
                        final ProductCategory category = c;
                        rows.put(c.getProductCategoryId(), projection(FrontCategoryRow.class, key -> switch (key) {
                            case "getCategoryId" -> category.getProductCategoryId(); case "getName" -> category.getCategoryName();
                            case "getParentId" -> category.getParentCategory() == null ? null : category.getParentCategory().getProductCategoryId(); default -> null;
                        }));
                    }
                }
                return new ArrayList<>(rows.values());
            }
            throw new AssertionError(method.getName());
        });
        return new FrontCatalogService(repo);
    }
    @SuppressWarnings("unchecked")
    private <T> T projection(Class<T> type, java.util.function.Function<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> values.apply(m.getName()));
    }
    private Product product(int id, String name) { Product p = new Product(); p.setProductId(id); p.setProductName(name); p.setStatus((byte)1); p.setProductDesc("資料庫描述"); return p; }
    private ProductSku sku(Product p, int id, int price, int status) { ProductSku s = new ProductSku(); s.setSkuId(id); s.setSkuName("規格" + id); s.setPrice(price); s.setStock(10); s.setStatus((byte)status); s.setProduct(p); p.getProductSkus().add(s); return s; }
    private void image(ProductSku s, int id, int order) { ProductImage i = new ProductImage(); i.setImageId(id); i.setSortOrder(order); i.setImageData(new byte[]{1}); i.setProductSku(s); s.getProductImages().add(i); }
    // 首頁會向 PromoService 要活動；這些測試只看商品，所以給一個沒有任何進行中活動的版本（不連資料庫）
    private com.fruitude.promo.model.PromoService noPromos() {
        return new com.fruitude.promo.model.PromoService() {
            @Override public com.fruitude.promo.model.HomePromos findHomePromos(com.fruitude.promo.model.MemberPromoState state) {
                return new com.fruitude.promo.model.HomePromos(List.of(), List.of());
            }
        };
    }
    private MockMvc mvc(FrontCatalogService service) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver view = new ThymeleafViewResolver(); view.setTemplateEngine(engine); view.setCharacterEncoding("UTF-8");
        return MockMvcBuilders.standaloneSetup(new FrontProductController(service, noPromos(), new com.fruitude.orders.model.OrdersService())).setViewResolvers(view).build();
    }
    @Test public void pricesImagesAndVisibilityComeFromActiveSkus() {
        Product p = product(1, "測試芒果"); sku(p, 1, 1, 0); ProductSku available = sku(p, 2, 200, 1); image(available, 9, 2); image(available, 10, 1);
        Product noActiveSku = product(2, "隱藏商品"); sku(noActiveSku, 3, 50, 0);
        List<FrontCatalogService.ProductView> result = service(List.of(p, noActiveSku)).getProducts();
        assertEquals(1, result.size()); assertEquals(Integer.valueOf(200), result.get(0).price()); assertEquals(Integer.valueOf(2), result.get(0).skuId()); assertEquals(Integer.valueOf(10), result.get(0).imageId());
        assertFalse(result.get(0).giftBox());
        assertEquals(List.of(10, 9), result.get(0).skus().get(0).imageIds());
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
    @Test public void homeCardsExposeActiveSkuChoicesQuantityAndCartButtonWithoutDescription() throws Exception {
        Product p = product(5, "可選規格芒果");
        ProductSku small = sku(p, 42, 345, 1); small.setSkuName("小盒"); small.setStock(3);
        ProductSku large = sku(p, 43, 678, 1); large.setSkuName("大盒"); large.setStock(7); image(large, 99, 0);
        ProductSku offline = sku(p, 44, 999, 0); offline.setSkuName("已下架規格");
        String html = mvc(service(List.of(p))).perform(get("/shop/front/").contextPath("/shop"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("products-purchase-card"));
        assertTrue(html.contains("id=\"home-sku-5\""));
        assertTrue(html.contains("value=\"42\"")); assertTrue(html.contains("value=\"43\""));
        assertTrue(html.contains("data-sku-name=\"大盒\""));
        assertTrue(html.contains("data-price=\"678\"")); assertTrue(html.contains("data-stock=\"10\"")); // 狀態 1：可訂購數量 = min(10, stock)
        assertTrue(html.contains("/shop/product/image/99"));
        assertTrue(html.contains("id=\"home-qty-5\""));
        assertTrue(html.contains("home-card-add")); assertFalse(html.contains("目前缺貨"));
        assertFalse(html.contains("已下架規格")); assertFalse(html.contains("資料庫描述"));
        assertTrue(html.contains("/shop/front/css/home-product-cards.css"));
    }
    @Test public void shortageAndRetiringSkusRenderWithQuantityPolicy() throws Exception {
        Product p = product(22, "狀態測試商品");
        ProductSku shortage = sku(p, 100, 100, 2); shortage.setStock(0);
        ProductSku retiring = sku(p, 101, 200, 3); retiring.setStock(4); retiring.setInboundQty(8); retiring.setOutboundQty(3);
        FrontCatalogService service = service(List.of(p));
        assertEquals(Integer.valueOf(5), service.getProducts().get(0).skus().get(0).stock());
        assertEquals(Integer.valueOf(5), service.getProducts().get(0).skus().get(1).stock());
        String html = mvc(service).perform(get("/front/")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-sku-status=\"2\"")); assertTrue(html.contains("data-sku-status=\"3\""));
        assertTrue(html.contains("（缺貨）")); assertTrue(html.contains("（即將售完）"));
        String detail = mvc(service).perform(get("/front/product/view/").param("productId","22")).andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("data-sku-status=\"2\"")); assertTrue(detail.contains("data-stock=\"5\""));
    }
    @Test public void aliasIsPreferredAndBlankAliasFallsBackAcrossFrontPages() throws Exception {
        Product p = product(22, "測試水果");
        ProductSku named = sku(p, 100, 100, 1); named.setSkuName("原名禮盒"); named.setAnotherName("  特選小盒  ");
        ProductSku blank = sku(p, 101, 200, 1); blank.setSkuName("原名大盒"); blank.setAnotherName(" \t ");
        FrontCatalogService service = service(List.of(p));
        assertEquals("特選小盒", service.getProducts().get(0).skus().get(0).name());
        assertEquals("原名大盒", service.getProducts().get(0).skus().get(1).name());
        assertTrue(service.getProducts().get(0).giftBox());
        for (String route : List.of("/front/", "/front/product/view/")) {
            String html = mvc(service).perform(get(route).param("productId","22")).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("特選小盒")); assertTrue(html.contains("原名大盒")); assertFalse(html.contains(">原名禮盒<"));
        }
        assertEquals("特選小盒", named.getDisplayName()); assertEquals("原名大盒", blank.getDisplayName());
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

    @Test public void mixedSoldOutSkuIsVisibleButCannotBecomeDefaultAndAllSoldOutIsHidden() throws Exception {
        Product p=product(90,"售完顯示測試");sku(p,900,1,4).setSkuName("售完規格");sku(p,901,200,1).setSkuName("可購買規格");sku(p,902,100,0).setSkuName("下架規格");
        Product all=product(91,"全售完商品");sku(all,903,100,4);
        var service=service(List.of(p,all));assertEquals(1,service.getProducts().size());
        assertEquals(Integer.valueOf(901),service.getProducts().get(0).skuId());
        assertEquals(Integer.valueOf(0),service.getProducts().get(0).skus().get(0).stock());
        String html=mvc(service).perform(get("/front/")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("售完規格（售完）"));assertFalse(html.contains("全售完商品"));assertFalse(html.contains("下架規格"));
    }
}
