package com.fruitude.product.model;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public, masked review data only. Never returns member IDs or unmasked accounts. */
@Service
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class FrontProductReviewService {
    private final NamedParameterJdbcTemplate jdbc;
    public FrontProductReviewService(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Review(String account, int stars, String skuName, String text, LocalDateTime date) {}
    public record StarCount(int stars,long count) {}
    public record ReviewPage(List<Review> reviews, long count, double average, int page, int size, boolean hasMore,
                             long totalCount,List<StarCount> distribution) {
        public static ReviewPage empty() { return new ReviewPage(List.of(),0,0,0,3,false,0,
            List.of(new StarCount(5,0),new StarCount(4,0),new StarCount(3,0),new StarCount(2,0),new StarCount(1,0))); }
    }
    private static final String FROM = " FROM orders_detail d JOIN product_sku s ON s.sku_id=d.sku_id "
        +"JOIN orders o ON o.orders_id=d.orders_id LEFT JOIN member m ON m.member_id=o.member_id "
        +"WHERE s.product_id=:id AND d.comment_status=1 AND d.comment_star BETWEEN 1 AND 5 "
        +"AND d.comment_text IS NOT NULL AND TRIM(d.comment_text)<>'' "
        +"AND EXISTS (SELECT 1 FROM product p JOIN product_sku sale ON sale.product_id=p.product_id "
        +"WHERE p.product_id=s.product_id AND sale.price>0 AND ((p.status=1 AND sale.status IN (1,2,3,6)) OR (p.status=2 AND sale.status=6))) ";
    public ReviewPage read(int productId,int page,int size) { return read(productId,page,size,"","latest"); }
    public ReviewPage read(int productId, int page, int size, String keyword, String sort) {
        if(productId<=0 || page<0 || page>100000 || size<1 || size>20) throw new IllegalArgumentException("評論查詢參數不正確");
        String query=keyword==null?"":keyword.trim();
        if(query.length()>200)throw new IllegalArgumentException("搜尋文字最多200字");
        String order=switch(sort==null?"latest":sort) {
            case "latest" -> "d.comment_date DESC,d.orders_detail_id DESC";
            case "highest" -> "d.comment_star DESC,d.comment_date DESC,d.orders_detail_id DESC";
            case "lowest" -> "d.comment_star ASC,d.comment_date DESC,d.orders_detail_id DESC";
            default -> throw new IllegalArgumentException("評論排序不正確");
        };
        var params=new HashMap<String,Object>();params.put("id",productId);
        String histogram="";for(int star=1;star<=5;star++)histogram+=", COALESCE(SUM(d.comment_star="+star+"),0) AS star"+star;
        var summary=jdbc.queryForMap("SELECT COUNT(*) AS amount, COALESCE(ROUND(AVG(d.comment_star),1),0) AS average"+histogram+FROM,params);
        long total=((Number)summary.get("amount")).longValue();
        double average=((Number)summary.get("average")).doubleValue();
        var distribution=new ArrayList<StarCount>();
        for(int star=5;star>=1;star--)distribution.add(new StarCount(star,((Number)summary.get("star"+star)).longValue()));
        String filtered=FROM;
        if(!query.isEmpty()) {
            params.put("keyword","%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            filtered+="AND d.comment_text LIKE :keyword ESCAPE '!' ";
        }
        long count=query.isEmpty()?total:jdbc.queryForObject("SELECT COUNT(*)"+filtered,params,Long.class);
        long offset=(long)page*size;
        if(offset>=count)return new ReviewPage(List.of(),count,average,page,size,false,total,List.copyOf(distribution));
        params.put("limit",size);params.put("offset",offset);
        var reviews=jdbc.query("SELECT m.member_account,d.comment_star,s.sku_name,s.another_name,d.comment_text,d.comment_date"+filtered
            +"ORDER BY "+order+" LIMIT :limit OFFSET :offset",params,(rs,n)-> {
                var date=rs.getTimestamp("comment_date");
                return new Review(maskAccount(rs.getString("member_account")),rs.getInt("comment_star"),
                    ProductSku.resolveDisplayName(rs.getString("sku_name"),rs.getString("another_name")),rs.getString("comment_text"),date==null?null:date.toLocalDateTime());
            });
        return new ReviewPage(List.copyOf(reviews),count,average,page,size,offset+reviews.size()<count,total,List.copyOf(distribution));
    }
    public static String maskAccount(String account) {
        String clean=account==null?"":account.replaceAll("[^A-Za-z0-9]", "");
        if(clean.isEmpty())return "會員";
        if(clean.length()==1)return "*";
        return clean.charAt(0)+"*".repeat(Math.max(1,clean.length()-2))+clean.charAt(clean.length()-1);
    }
}
