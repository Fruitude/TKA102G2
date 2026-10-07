package com.fruitude.product;

import com.fruitude.product.model.*;
import com.fruitude.product.controller.FrontProductController;
import com.fruitude.orders.model.CheckoutItem;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class FrontLiveProductTest {
    private final int[] stock = {10}, price = {199}, status = {1}, inbound = {0}, outbound = {0};
    private final String[] alias = {null};
    private FrontCatalogService service() {
        LiveSkuRow row = (LiveSkuRow) Proxy.newProxyInstance(LiveSkuRow.class.getClassLoader(), new Class<?>[]{LiveSkuRow.class}, (p,m,a) -> switch(m.getName()) {
            case "getSkuId" -> 6; case "getName" -> "草莓"; case "getSkuName" -> "小盒"; case "getAnotherName" -> alias[0];
            case "getInboundQty" -> inbound[0]; case "getOutboundQty" -> outbound[0]; case "getPrice" -> price[0]; case "getStock" -> stock[0]; case "getProductStatus" -> 1; case "getSkuStatus" -> status[0]; default -> null;
        });
        ProductRepository repo = (ProductRepository) Proxy.newProxyInstance(ProductRepository.class.getClassLoader(), new Class<?>[]{ProductRepository.class}, (p,m,a) -> {
            if (m.getName().equals("findFrontRows")) return List.of();
            if (m.getName().equals("findLiveSkus")) return ((List<?>)a[0]).contains(6) ? List.of(row) : List.of();
            throw new AssertionError(m.getName());
        });
        return new FrontCatalogService(repo);
    }
    private CheckoutItem item(int qty, int amount) {
        CheckoutItem item = new CheckoutItem(); item.setSkuId(6); item.setQty(qty); item.setPrice(amount); return item;
    }
    @Test public void liveCartIgnoresCatalogCacheAndHasNoStoreHeader() throws Exception {
        FrontCatalogService service = service(); service.getProducts();
        var mvc = MockMvcBuilders.standaloneSetup(new FrontProductController(service)).build();
        var first = mvc.perform(get("/front/api/cart-products").param("skuIds", "6")).andReturn().getResponse();
        assertEquals(200, first.getStatus()); assertTrue(first.getHeader("Cache-Control").contains("no-store")); assertTrue(first.getContentAsString().contains("\"price\":199"));
        price[0] = 299; stock[0] = 4; // 狀態 1：可訂購數量 = min(10, stock)，即時反映在購物車查詢
        var changed = mvc.perform(get("/front/api/cart-products").param("skuIds", "6")).andReturn().getResponse().getContentAsString();
        assertTrue(changed.contains("\"price\":299")); assertTrue(changed.contains("\"available\":true")); assertTrue(changed.contains("\"stock\":4"));
        assertEquals(400, mvc.perform(get("/front/api/cart-products").param("skuIds", "-1")).andReturn().getResponse().getStatus());
    }
    @Test public void checkoutRejectsOldPriceOfflineMissingAndCombinedQuantityOverStock() {
        FrontCatalogService service = service();
        CheckoutItem valid = item(2,199); service.validateCheckoutItems(List.of(valid)); assertEquals("草莓", valid.getProductName());
        rejects(service, List.of(item(1,198)));
        rejects(service, List.of(item(6,199), item(5,199)));
        status[0] = 0; rejects(service, List.of(item(1,199)));
        // 狀態 1：不能超過 stock，也不能超過一次 10 箱；stock 為 0 時不能訂購
        status[0] = 1; stock[0] = 8; service.validateCheckoutItems(List.of(item(8,199))); rejects(service, List.of(item(9,199)));
        stock[0] = 50; service.validateCheckoutItems(List.of(item(10,199)));
        rejects(service, List.of(item(11,199)));
        stock[0] = 0; rejects(service, List.of(item(1,199)));
        status[0] = 2; service.validateCheckoutItems(List.of(item(10,199))); rejects(service, List.of(item(11,199)));
        status[0] = 3; rejects(service, List.of(item(1,199)));
        stock[0] = 3; service.validateCheckoutItems(List.of(item(3,199)));
        rejects(service, List.of(item(4,199)));
        CheckoutItem missing = item(1,199); missing.setSkuId(99); rejects(service, List.of(missing));
        rejects(service, List.of(item(0,199)));
    }
    @Test public void retiringSkuUsesExpectedStockAndCombinesDuplicateRows() {
        status[0] = 3; stock[0] = 5; inbound[0] = 8; outbound[0] = 4;
        FrontCatalogService service = service();
        assertEquals(9, service.getLiveSkus(List.of(6)).get(0).stock());
        service.validateCheckoutItems(List.of(item(9,199)));
        rejects(service, List.of(item(5,199),item(5,199)));
        outbound[0] = 20; assertFalse(service.getLiveSkus(List.of(6)).get(0).available());
        status[0] = 1; assertEquals(5, service.getLiveSkus(List.of(6)).get(0).stock()); // 狀態 1：min(10, stock 5)
        status[0] = 2; assertTrue(service.getLiveSkus(List.of(6)).get(0).available());
    }
    @Test public void liveCartReturnsCurrentAliasAndFallsBackWhenCleared() {
        FrontCatalogService service = service();
        alias[0] = "  草莓分享盒  ";
        assertEquals("草莓分享盒", service.getLiveSkus(List.of(6)).get(0).skuName());
        alias[0] = " "; assertEquals("小盒", service.getLiveSkus(List.of(6)).get(0).skuName());
    }
    private void rejects(FrontCatalogService service, List<CheckoutItem> items) {
        try { service.validateCheckoutItems(items); fail("should reject checkout"); } catch (ProductUnavailableException expected) {}
    }
}
