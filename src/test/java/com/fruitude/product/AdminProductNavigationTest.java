package com.fruitude.product;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fruitude.controller.AdminIndexController;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class AdminProductNavigationTest {
    private MockMvc mvc(Object... controllers) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ThymeleafViewResolver view = new ThymeleafViewResolver();
        view.setTemplateEngine(engine);
        view.setCharacterEncoding("UTF-8");
        if (controllers.length == 0) {
            var psi = new com.fruitude.controller.AdminPsiController();
            org.springframework.test.util.ReflectionTestUtils.setField(psi, "vendorSvc", new com.fruitude.vendor.model.VendorService() {
                @Override public java.util.List<com.fruitude.vendor.model.VendorVO> getActiveVendors() { return java.util.List.of(); }
            });
            controllers = new Object[]{new AdminIndexController(), psi};
        }
        return MockMvcBuilders.standaloneSetup(controllers).setViewResolvers(view).build();
    }

    @Test public void productAndVendorAreIndependentPagesWithSharedStyles() throws Exception {
        MockMvc mvc = mvc();
        var productResponse = mvc.perform(get("/admin/psi/product")).andReturn().getResponse();
        assertEquals(200, productResponse.getStatus());
        String product = productResponse.getContentAsString();
        for (String label : new String[]{"分類管理", "商品總覽", "新增商品", "商品查詢", "庫存查詢"}) {
            assertTrue(product.contains(label));
        }
        assertTrue(product.contains("id=\"accordionSidebar\""));
        assertTrue(product.contains("href=\"/css/sb-admin-2.min.css\""));
        assertTrue(product.contains("href=\"/admin/psi/product\""));
        assertTrue(product.contains("href=\"/admin/psi\""));
        assertFalse(product.contains("Shipping System"));
        assertFalse(product.contains("product-management.js"));
        assertTrue(product.contains("role=\"tablist\""));
        assertTrue(product.contains("data-url=\"/product/listAllProduct\""));
        assertTrue(product.contains("src=\"/admin/js/product-tabs.js\""));
        assertFalse(product.replaceAll("(?s)<!--.*?-->", "").contains("th:href"));
        var vendorResponse = mvc.perform(get("/admin/psi/vendor")).andReturn().getResponse();
        assertEquals(200, vendorResponse.getStatus());
        String vendor = vendorResponse.getContentAsString();
        assertTrue(vendor.contains("供應商管理"));
        assertTrue(vendor.contains("id=\"accordionSidebar\""));
        assertTrue(vendor.contains("href=\"/css/sb-admin-2.min.css\""));
        assertFalse(vendor.contains("showProductManagement"));
    }

    @Test public void previousEntrancesRedirectToTheNewParallelRoutes() throws Exception {
        MockMvc mvc = mvc();
        for (String old : new String[]{"/admin/vendor", "/admin/psi/vendor.html"}) {
            var response = mvc.perform(get(old)).andReturn().getResponse();
            assertEquals(302, response.getStatus());
            assertEquals("/admin/psi/vendor", response.getRedirectedUrl());
        }
        var psi = mvc.perform(get("/admin/psi")).andReturn().getResponse();
        assertEquals(200, psi.getStatus());
        assertTrue(psi.getContentAsString().contains("Shipping System"));
        for (String old : new String[]{"/admin/psi/", "/admin/psi/index.html"}) {
            assertEquals("/admin/psi", mvc.perform(get(old)).andReturn().getResponse().getRedirectedUrl());
        }
        var product = mvc.perform(get("/admin/psi/productmanagement.html")).andReturn().getResponse();
        assertEquals(302, product.getStatus());
        assertEquals("/admin/psi/product", product.getRedirectedUrl());
        assertEquals("/admin/psi/product", mvc.perform(get("/admin/product")).andReturn().getResponse().getRedirectedUrl());
    }

    @Test public void linksRespectDeploymentContextPath() throws Exception {
        String html = mvc().perform(get("/shop/admin/psi/product").contextPath("/shop"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("href=\"/shop/admin/psi/product\""));
        assertTrue(html.contains("href=\"/shop/admin/psi\""));
        assertTrue(html.contains("data-url=\"/shop/product/addProduct\""));
    }
    @Test public void queriesRenderFilteredDatabaseProductsAndSkuStock() throws Exception {
        var mango = new com.fruitude.product.model.Product();
        mango.setProductId(1); mango.setProductName("愛文芒果");
        var berry = new com.fruitude.product.model.Product();
        berry.setProductId(2); berry.setProductName("香水草莓");
        var low = new com.fruitude.product.model.ProductSku();
        low.setSkuId(10); low.setSkuName("芒果小盒"); low.setProduct(mango); low.setStock(2); low.setSafetyStock(5);
        var normal = new com.fruitude.product.model.ProductSku();
        normal.setSkuId(20); normal.setSkuName("草莓大盒"); normal.setProduct(berry); normal.setStock(20); normal.setSafetyStock(5);
        var products = new com.fruitude.product.model.ProductService() {
            @Override public java.util.List<com.fruitude.product.model.Product> getAll() {
                return java.util.List.of(mango, berry);
            }
        };
        var skus = new com.fruitude.product.model.ProductSkuService() {
            @Override public java.util.List<com.fruitude.product.model.ProductSku> getAll() {
                return java.util.List.of(low, normal);
            }
        };
        MockMvc mvc = mvc(new com.fruitude.controller.AdminProductQueryController(products, skus));
        String search = mvc.perform(get("/admin/psi/product/search").param("keyword", "芒果"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(search.contains("愛文芒果")); assertFalse(search.contains("香水草莓"));
        String stock = mvc.perform(get("/admin/psi/product/stock").param("belowOnly", "true"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(stock.contains("芒果小盒")); assertFalse(stock.contains("草莓大盒"));
        String empty = mvc.perform(get("/admin/psi/product/search").param("keyword", "不存在"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(empty.contains("沒有符合條件的商品"));
    }
    @org.springframework.stereotype.Controller
    public static class OverviewFixture {
        @org.springframework.web.bind.annotation.GetMapping("/overview-test")
        public String page(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "測試總覽") String name, org.springframework.ui.Model model) {
            var row = (com.fruitude.product.model.ProductOverview) java.lang.reflect.Proxy.newProxyInstance(
                com.fruitude.product.model.ProductOverview.class.getClassLoader(),
                new Class<?>[]{com.fruitude.product.model.ProductOverview.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getProductId" -> 42;
                    case "getProductName" -> name;
                    case "getVendorName" -> "測試供應商";
                    case "getImageId" -> 9;
                    case "getStatus" -> 0;
                    default -> 0;
                });
            model.addAttribute("productListData", java.util.List.of(row));
            model.addAttribute("overviewLightweight", true);
            model.addAttribute("overviewImageIds", java.util.Map.of(42, java.util.List.of(9, 10, 11)));
            model.addAttribute("overviewStockStatuses", java.util.Map.of(42, java.util.List.of("低於安全庫存", "庫存低於10", "待進貨超過100", "未設安全庫存")));
            model.addAttribute("currentPage", 1); model.addAttribute("pageSize", 10);
            model.addAttribute("stockFilter", "abnormal");
            model.addAttribute("totalPages", 1); model.addAttribute("totalProducts", 1); model.addAttribute("statusFilter", "off");
            return "admin/psi/productmanagement/product/listAllProduct";
        }
    }

    @Test public void overviewStartsHiddenAndImagesOnlyHaveDeferredUrls() throws Exception {
        var response = mvc(new OverviewFixture()).perform(get("/shop/overview-test").contextPath("/shop"))
            .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        String html = response.getContentAsString();
        assertTrue(html.contains("class=\"products-loading\""));
        assertTrue(html.contains("測試總覽"));
        assertTrue(html.contains("測試供應商"));
        assertTrue(html.contains("data-src=\"/shop/product/image/9\""));
        assertTrue(html.contains("data-image-ids=\"9,10,11\""));
        assertTrue(html.contains("data-image-base=\"/shop/product/image/\""));
        assertTrue(html.contains("庫存狀態"));
        assertTrue(html.contains("待進貨超過100"));
        assertTrue(html.contains("未設安全庫存"));
        assertTrue(html.contains("stock-alert stock-danger"));
        assertTrue(html.contains("colspan=\"9\""));
        assertTrue(html.contains("value=\"abnormal\" selected=\"selected\""));
        assertFalse(html.contains(" src=\"/shop/product/image/9\""));
        assertTrue(html.contains("product-off"));
        assertTrue(html.contains("id=\"status-filter\""));
        assertTrue(html.contains("value=\"off\" selected=\"selected\""));
        assertFalse(html.contains("id=\"hide-offline\""));
        assertTrue(html.contains("src=\"/shop/admin/js/product-pagination.js\""));
    }
    @Test public void longProductNamesShowTenCharactersWithFullNameInTooltip() throws Exception {
        String name = "一二三四五六七八九十十一十二";
        String html = mvc(new OverviewFixture()).perform(get("/overview-test").param("name", name))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("title=\"" + name + "\""));
        assertTrue(html.contains(">一二三四五六七八九十…</span>"));
        String exact = mvc(new OverviewFixture()).perform(get("/overview-test").param("name", "一二三四五六七八九十"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(exact.contains(">一二三四五六七八九十</span>"));
    }
}
