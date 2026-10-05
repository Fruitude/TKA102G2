package com.fruitude.product;

import static org.junit.Assert.*;
import com.fruitude.product.model.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

public class ProductOverviewPagingTest {
    @Test public void readsOnlyCurrentPageImageIdentifiersInTheirQueryOrder() {
        List<List<Integer>> requests = new ArrayList<>();
        ProductRepository repo = (ProductRepository) Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),
            new Class<?>[]{ProductRepository.class}, (proxy, method, args) -> {
                assertEquals("findOverviewImages", method.getName());
                @SuppressWarnings("unchecked") var ids = (List<Integer>) args[0]; requests.add(ids);
                return List.of(image(42, 9), image(42, 10), image(43, 11));
            });
        var service = new ProductService(); ReflectionTestUtils.setField(service, "repository", repo);
        assertEquals(Map.of(42, List.of(9, 10), 43, List.of(11)), service.getOverviewImageIds(List.of(42, 43)));
        assertTrue(service.getOverviewImageIds(List.of()).isEmpty());
        assertEquals(List.of(List.of(42, 43)), requests);
    }

    private ProductOverviewImage image(int productId, int imageId) {
        return new ProductOverviewImage() {
            public Integer getProductId() { return productId; }
            public Integer getImageId() { return imageId; }
        };
    }

    @Test public void onlyRequestsTheSelectedPageAndPassesTheOfflineFilter() {
        List<Pageable> requests = new ArrayList<>();
        List<Integer> filters = new ArrayList<>();
        ProductRepository repo = (ProductRepository) Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),
            new Class<?>[]{ProductRepository.class}, (proxy, method, args) -> {
                assertEquals("findOverviewPage", method.getName());
                filters.add((Integer) args[0]);
                Pageable requested = (Pageable) args[8]; requests.add(requested);
                int amount = requested.getPageNumber() >= 8 ? 0 : Math.min(20, 150 - (int)requested.getOffset());
                return new PageImpl<ProductOverview>(Collections.nCopies(amount, null), requested, 150);
            });
        ProductService service = new ProductService();
        ReflectionTestUtils.setField(service, "repository", repo);
        var first = service.getOverviewPage(2, 20, "on");
        assertEquals(20, first.getContent().size());
        assertEquals(20, requests.get(0).getOffset());
        assertEquals(Integer.valueOf(1), filters.get(0));
        assertEquals(8, first.getTotalPages());
        service.getOverviewPage(-1, 999, "all");
        assertNull(filters.get(1));
        assertEquals(0, requests.get(1).getPageNumber());
        assertEquals(10, requests.get(1).getPageSize());
        var last = service.getOverviewPage(99, 20, "off");
        assertEquals(Integer.valueOf(0), filters.get(2));
        assertEquals(7, last.getNumber());
        assertEquals(10, last.getNumberOfElements());
        assertEquals(140, requests.get(requests.size()-1).getOffset());
    }
}
