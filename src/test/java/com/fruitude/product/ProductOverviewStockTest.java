package com.fruitude.product;

import com.fruitude.product.model.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;

public class ProductOverviewStockTest {
    @Test public void previousLowStockSelectionMigratesToCombinedSafetyFilter() {
        assertEquals("below-safety", ProductService.normalizeStockFilter("low-stock"));
        assertEquals("below-safety", ProductService.normalizeStockFilter("below-safety"));
        assertEquals("all", ProductService.normalizeStockFilter("invalid"));
    }
    @Test public void combinesAlertsSuppressesNormalAndChoosesOnePendingLevel() {
        var requests = new ArrayList<List<Integer>>();
        var repo = (ProductRepository) Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),
            new Class<?>[]{ProductRepository.class}, (proxy, method, args) -> {
                assertEquals("findOverviewStock", method.getName());
                @SuppressWarnings("unchecked") var ids = (List<Integer>) args[0]; requests.add(ids);
                return List.of(stock(1, 1, 1, 1, 1, 101, 101), stock(2, 0, 0, 0, 0, 50, 50),
                    stock(3, 0, 0, 0, 0, 100, 51), stock(4, 0, 0, 0, 0, null, null));
            });
        var service = new ProductService(); ReflectionTestUtils.setField(service, "repository", repo);
        var statuses = service.getOverviewStockStatuses(List.of(1, 2, 3, 4, 5));
        assertEquals(List.of("低於安全庫存", "庫存低於10", "庫存高於100", "待進貨超過100", "待出貨超過100", "未設安全庫存"), statuses.get(1));
        assertEquals(List.of("正常"), statuses.get(2));
        assertEquals(List.of("待進貨超過50", "待出貨超過50"), statuses.get(3));
        assertEquals(List.of("正常"), statuses.get(4));
        assertEquals(List.of("正常"), statuses.get(5));
        assertTrue(service.getOverviewStockStatuses(List.of()).isEmpty());
        assertEquals(List.of(List.of(1, 2, 3, 4, 5)), requests);
    }

    private ProductOverviewStock stock(int id, int below, int low, int high, int unset, Integer inbound, Integer outbound) {
        return new ProductOverviewStock() {
            public Integer getProductId() { return id; }
            public Integer getBelowSafety() { return below; }
            public Integer getLowStock() { return low; }
            public Integer getHighStock() { return high; }
            public Integer getUnsetSafety() { return unset; }
            public Integer getMaxInbound() { return inbound; }
            public Integer getMaxOutbound() { return outbound; }
        };
    }
}
