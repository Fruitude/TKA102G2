package com.fruitude.product.model;
import java.util.*;
import java.util.regex.Pattern;
import com.fruitude.product.model.FrontCatalogService.ProductView;

/** Fuzzy name matching over the already loaded sellable catalog; no extra database read. */
public final class ProductRecommendations {
    private ProductRecommendations() {}
    private static final Set<String> GENERIC=Set.of("商品","規格","測試","禮盒","家庭","分享","鮮果","推薦","一般","頂級","限定","嚴選","精選","新鮮","包裝","組合");
    private static Set<String> keywords(String name) {
        var result=new LinkedHashSet<String>();
        var matcher=Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+").matcher(name==null?"":name.toLowerCase(Locale.ROOT));
        while(matcher.find()) {
            String word=matcher.group();
            if(Character.UnicodeScript.of(word.codePointAt(0))==Character.UnicodeScript.HAN) {
                int[] chars=word.codePoints().toArray();
                for(int i=0;i+1<chars.length;i++) {String key=new String(chars,i,2);if(!GENERIC.contains(key))result.add(key);}
            } else if(word.length()>=2 && !word.matches("[0-9]+"))result.add(word);
        }
        return result;
    }
    private record Match(ProductView product,int score) {}
    public static List<ProductView> recommend(ProductView selected,List<ProductView> catalog,int limit) {
        if(selected==null || limit<=0)return List.of();
        var keys=keywords(selected.name());
        var candidates=new LinkedHashMap<Integer,ProductView>();
        for(var product:catalog)if(!product.productId().equals(selected.productId()))
            candidates.putIfAbsent(product.productId(),product);
        // Related names come first. Remaining sellable products fill unused positions, without duplicates.
        return candidates.values().stream()
            .map(p->new Match(p,(int)keys.stream().filter(key->p.name()!=null && p.name().toLowerCase(Locale.ROOT).contains(key)).count()))
            .sorted(Comparator.comparingInt(Match::score).reversed()
                .thenComparing(m->m.product().giftBox()!=selected.giftBox())
                .thenComparing(m->m.product().productId()))
            .limit(Math.min(5,limit)).map(Match::product).toList();
    }
}
