package com.fruitude.product;
import java.util.*;
import com.fruitude.product.model.*;
import com.fruitude.product.model.FrontCatalogService.ProductView;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProductRecommendationsTest {
    @Test public void rankingsReuseListsUntilDailyRefreshAndPublishBothTogether() throws Exception {
        var bestIds=new java.util.concurrent.atomic.AtomicReference<List<Integer>>(List.of(3,1,2));
        var ratedIds=new java.util.concurrent.atomic.AtomicReference<List<Integer>>(List.of(2,1,3));
        var counts=new HashMap<String,Integer>();
        ProductRepository repository=(ProductRepository)java.lang.reflect.Proxy.newProxyInstance(
            ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,method,args)->{
                String name=method.getName();counts.merge(name,1,Integer::sum);
                if(name.equals("findBestSellerProductIds")) {
                    var from=(java.time.LocalDateTime)args[0];var to=(java.time.LocalDateTime)args[1];
                    assertEquals(to.toLocalDate().minusMonths(6).atStartOfDay(),from);
                    return bestIds.get();
                }
                if(name.equals("findTopRatedProductIds"))return ratedIds.get();
                if(name.equals("findFrontRows"))return java.util.stream.IntStream.rangeClosed(1,3).mapToObj(id->
                    (ProductRepository.FrontCatalogRow)java.lang.reflect.Proxy.newProxyInstance(
                        ProductRepository.FrontCatalogRow.class.getClassLoader(),new Class<?>[]{ProductRepository.FrontCatalogRow.class},
                        (p,m,a)->switch(m.getName()) {
                            case "getProductId","getSkuId"->id;case "getName","getSkuName"->"水果"+id;
                            case "getSkuStatus"->1;case "getPrice"->100;case "getStock"->10;default->null;
                        })).toList();
                return List.of();
            });
        var service=new FrontCatalogService(repository);
        var firstBest=service.getBestSellers();var firstRated=service.getTopRatedProducts();
        assertEquals(List.of(3,1,2),firstBest.stream().map(ProductView::productId).toList());
        assertEquals(List.of(2,1,3),firstRated.stream().map(ProductView::productId).toList());
        bestIds.set(List.of(1,3));ratedIds.set(List.of(3,2));
        for(int i=0;i<10;i++){assertSame(firstBest,service.getBestSellers());assertSame(firstRated,service.getTopRatedProducts());}
        assertEquals(Integer.valueOf(1),counts.get("findFrontRows"));
        assertEquals(Integer.valueOf(1),counts.get("findBestSellerProductIds"));
        assertEquals(Integer.valueOf(1),counts.get("findTopRatedProductIds"));
        service.refreshRankedCatalog();
        assertEquals(List.of(1,3),service.getBestSellers().stream().map(ProductView::productId).toList());
        assertEquals(List.of(3,2),service.getTopRatedProducts().stream().map(ProductView::productId).toList());
        assertEquals(List.of(3,1,2),firstBest.stream().map(ProductView::productId).toList());
        var schedule=FrontCatalogService.class.getMethod("refreshRankedCatalog").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertEquals("0 0 0 * * *",schedule.cron());assertEquals("Asia/Taipei",schedule.zone());
    }
    private ProductRepository.CatalogPriorityRow priority(int id,int category,int favorite,int ordered,int stock) {
        return new ProductRepository.CatalogPriorityRow() {
            public Integer getProductId(){return id;} public Integer getCategoryId(){return category;}
            public Integer getFavoriteMatch(){return favorite;} public Integer getOrderMatch(){return ordered;}
            public Integer getAboveSafety(){return stock;}
        };
    }
    @Test public void catalogUsesExactRequestedCombinationOrderAndKeepsAllProducts() {
        var products=List.of(product(5,"其他"),product(4,"收藏訂購"),product(3,"訂購庫存"),product(2,"收藏庫存"),product(1,"三條件"));
        var rows=List.of(priority(1,1,1,1,1),priority(2,2,1,0,1),priority(3,3,0,1,1),priority(4,4,1,1,0),priority(5,5,0,0,1));
        for(int attempt=0;attempt<10;attempt++) assertEquals(List.of(1,2,3,4,5),
            FrontCatalogService.rankCatalog(products,rows,true,List.of()).stream().map(ProductView::productId).toList());
    }
    @Test public void browserFavoritesUseSameCategoryAndAreIgnoredForAnonymousVisitors() {
        var products=List.of(product(1,"收藏相似"),product(2,"曾訂購"),product(3,"剩餘"));
        var rows=List.of(priority(99,10,0,0,0),priority(1,10,0,0,1),priority(2,20,0,1,1),priority(3,30,0,0,0));
        assertEquals(List.of(1,2,3),FrontCatalogService.rankCatalog(products,rows,true,List.of(99))
            .stream().map(ProductView::productId).toList());
        var anonymous=FrontCatalogService.rankCatalog(products,rows,false,List.of(99));
        assertEquals(Set.of(1,2),new HashSet<>(anonymous.subList(0,2).stream().map(ProductView::productId).toList()));
        assertEquals(Integer.valueOf(3),anonymous.get(2).productId());
        assertEquals(4,FrontCatalogService.catalogPriority(true,true,false,false));
        assertEquals(4,FrontCatalogService.catalogPriority(true,false,true,false));
        assertEquals(4,FrontCatalogService.catalogPriority(true,false,false,true));
    }
    private ProductView product(int id,String name) {return new ProductView(id,name,"",100,100,id,null,false,List.of(),10,0d);}
    @Test public void prefersFruitAndVarietyThenFillsWithOtherProducts() {
        var selected=product(1,"香水草莓");
        var result=FrontCatalogService.recommend(selected,List.of(selected,product(2,"大湖草莓"),product(3,"香水芒果"),product(4,"富士蘋果")),5);
        assertEquals(List.of(2,3,4),result.stream().map(ProductView::productId).toList());
    }
    @Test public void fillsWithoutKeywordsAndDeduplicatesFallback() {
        var selected=product(1,"A");
        var catalog=new ArrayList<ProductView>();catalog.add(selected);
        for(int i=2;i<=8;i++) {var other=product(i,"水果"+i);catalog.add(other);catalog.add(other);}
        var result=FrontCatalogService.recommend(selected,catalog,5);
        assertEquals(List.of(2,3,4,5,6),result.stream().map(ProductView::productId).toList());
        assertEquals(2,FrontCatalogService.recommend(selected,catalog,2).size());
    }
    @Test public void capsSixtyAndDoesNotMatchOnlyGenericGiftBoxText() {
        var selected=product(1,"草莓禮盒");var catalog=new ArrayList<ProductView>();
        catalog.add(selected);catalog.add(product(99,"蘋果禮盒"));
        for(int i=2;i<82;i++)catalog.add(product(i,"草莓"+i));
        var result=FrontCatalogService.recommend(selected,catalog,100);
        assertEquals(60,result.size());assertFalse(result.stream().anyMatch(p->p.productId()==99));
        assertTrue(FrontCatalogService.recommend(null,catalog,5).isEmpty());
    }
}
