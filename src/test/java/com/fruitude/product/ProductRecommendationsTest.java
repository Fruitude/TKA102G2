package com.fruitude.product;
import java.util.*;
import com.fruitude.product.model.*;
import com.fruitude.product.model.FrontCatalogService.ProductView;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProductRecommendationsTest {
    private ProductView product(int id,String name) {return new ProductView(id,name,"",100,100,id,null,false,List.of(),10,0d);}
    @Test public void prefersFruitAndVarietyThenFillsWithOtherProducts() {
        var selected=product(1,"香水草莓");
        var result=ProductRecommendations.recommend(selected,List.of(selected,product(2,"大湖草莓"),product(3,"香水芒果"),product(4,"富士蘋果")),5);
        assertEquals(List.of(2,3,4),result.stream().map(ProductView::productId).toList());
    }
    @Test public void fillsWithoutKeywordsAndDeduplicatesFallback() {
        var selected=product(1,"A");
        var catalog=new ArrayList<ProductView>();catalog.add(selected);
        for(int i=2;i<=8;i++) {var other=product(i,"水果"+i);catalog.add(other);catalog.add(other);}
        var result=ProductRecommendations.recommend(selected,catalog,5);
        assertEquals(List.of(2,3,4,5,6),result.stream().map(ProductView::productId).toList());
        assertEquals(2,ProductRecommendations.recommend(selected,catalog,2).size());
    }
    @Test public void capsFiveAndDoesNotMatchOnlyGenericGiftBoxText() {
        var selected=product(1,"草莓禮盒");var catalog=new ArrayList<ProductView>();
        catalog.add(selected);catalog.add(product(99,"蘋果禮盒"));
        for(int i=2;i<10;i++)catalog.add(product(i,"草莓"+i));
        var result=ProductRecommendations.recommend(selected,catalog,50);
        assertEquals(5,result.size());assertFalse(result.stream().anyMatch(p->p.productId()==99));
        assertTrue(ProductRecommendations.recommend(null,catalog,5).isEmpty());
    }
}
