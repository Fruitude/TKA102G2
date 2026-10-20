package com.fruitude.quiz.model;

import java.util.*;
import org.springframework.stereotype.Service;
import com.fruitude.product.model.FrontCatalogService;

/** Selects buyable, distinct products from this theme in descending score order. */
@Service
public class QuizRecommendationService {
    private final FrontCatalogService catalog;
    public QuizRecommendationService(FrontCatalogService catalog){this.catalog=catalog;}
    public record Selection(Integer resultId,ChatQuizService.Result result) {}

    public Selection recommend(Map<String,Integer> scores,List<QuizResult> outcomes){
        var products=catalog.getProducts();
        // Each theme's configured outcomes define its own fruit pool; history never changes the ranking.
        var ranked=scores.entrySet().stream().filter(e->e.getValue()>0).sorted(
            Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).toList();
        var recommendations=new ArrayList<ChatQuizService.Recommendation>();QuizResult winning=null;var usedProducts=new HashSet<Integer>();
        for(var score:ranked){String name=QuizScoringService.fruitName(score.getKey());var outcome=outcomes.stream().filter(o->o.getResultName().equals(name)).findFirst().orElse(null);if(outcome==null)continue;
            // Fill from the highest-scoring fruit in this theme before considering its next ranked fruit.
            var candidates=products.stream().filter(p->!usedProducts.contains(p.productId())&&matchesFruit(p.name(),score.getKey()))
                .filter(p->p.skus().stream().anyMatch(QuizRecommendationService::buyable))
                .sorted(Comparator.comparing((FrontCatalogService.ProductView p)->p.averageRating()==null?0.0:p.averageRating()).reversed().thenComparing(FrontCatalogService.ProductView::productId)).toList();
            for(var product:candidates){
                if(!usedProducts.add(product.productId()))continue;
                var sku=product.skus().stream().filter(QuizRecommendationService::buyable).min(Comparator.comparing(FrontCatalogService.SkuView::price).thenComparing(FrontCatalogService.SkuView::skuId)).orElseThrow();
                recommendations.add(new ChatQuizService.Recommendation(name,outcome.getResultDesc(),product.productId(),product.name(),sku.price()));if(winning==null)winning=outcome;
                if(recommendations.size()==5)break;
            }
            if(recommendations.size()==5)break;
        }
        if(winning==null)throw new IllegalArgumentException("目前暫無適合的上架商品，請稍後再試。");
        var result=new ChatQuizService.Result("今天最合拍的果物："+winning.getResultName(),winning.getResultDesc(),List.copyOf(recommendations));
        return new Selection(winning.getResultId(),result);
    }
    private static boolean matchesFruit(String productName,String fruit){
        if(productName==null)return false;
        return switch(fruit){
            case "banana" -> productName.contains("香蕉")||productName.contains("山蕉");
            case "guava" -> productName.contains("芭樂")||productName.contains("帝王拔");
            case "pear" -> productName.contains("梨")&&!productName.contains("鳳梨");
            case "persimmon" -> productName.contains("柿");
            default -> productName.contains(QuizScoringService.fruitName(fruit));
        };
    }
    private static boolean buyable(FrontCatalogService.SkuView sku){return sku.stock()!=null&&sku.stock()>0&&sku.price()!=null&&sku.price()>0&&sku.skuStatus()!=null&&Set.of(1,2,3,6).contains(sku.skuStatus());}
}
