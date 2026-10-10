package com.fruitude.product.model;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrontCatalogFreshnessTest {
    @Test public void nextCatalogRequestReflectsChangedPriceWithoutCacheInvalidation() {
        int[] price={100};
        var row=(FrontCatalogRow)Proxy.newProxyInstance(FrontCatalogRow.class.getClassLoader(),new Class<?>[]{FrontCatalogRow.class},(p,m,a)->switch(m.getName()) {
            case "getProductId","getSkuId","getSkuStatus" -> 1;
            case "getName","getSkuName" -> "水果";
            case "getPrice" -> price[0]; default -> null;
        });
        var repo=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(p,m,a)->m.getName().equals("findFrontRows")?List.of(row):List.of());
        var service=new FrontCatalogService(repo);
        assertEquals(Integer.valueOf(100),service.getProducts().get(0).price());
        price[0]=200;
        assertEquals(Integer.valueOf(200),service.getProducts().get(0).price());
    }
}
