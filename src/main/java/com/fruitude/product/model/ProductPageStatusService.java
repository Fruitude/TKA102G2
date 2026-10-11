package com.fruitude.product.model;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Runs each submitted page item in a separate transaction and retains earlier commits on failure. */
@Service
public class ProductPageStatusService {
    public record Item(Integer productId,String productName,Byte status,String outcome,String message) {}
    public record Result(int updated,int skipped,int failed,Map<Integer,Byte> statuses,List<Item> results,List<Integer> confirmationIds) {}
    private final ProductService products;
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
    public ProductPageStatusService(ProductService products) { this.products=products; }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Result updatePageStatus(List<Integer> productIds,Byte status,boolean activateSkus) {
        if(status==null || (status!=0 && status!=1)) throw new IllegalArgumentException("狀態須為上架或下架");
        if(productIds==null || productIds.isEmpty() || productIds.size()>100
                || productIds.stream().anyMatch(id->id==null || id<=0))
            throw new IllegalArgumentException("請提供本頁有效的商品編號（最多100筆）");
        var results=new ArrayList<Item>();
        var states=new LinkedHashMap<Integer,Byte>();
        var confirmations=new ArrayList<Integer>();
        int updated=0,skipped=0,failed=0;
        for(var id:productIds.stream().distinct().sorted().toList()) {
            try {
                // REQUIRES_NEW on the other bean ensures commit (or rollback) completes before the next item.
                var item=products.updatePageItemStatus(id,status,activateSkus);
                results.add(item);states.put(id,item.status());
                if("UPDATED".equals(item.outcome()))updated++;else skipped++;
            } catch(ProductStatusConfirmationException e) {
                results.add(new Item(id,null,null,"CONFIRMATION",e.getMessage()));confirmations.add(id);failed++;
            } catch(RuntimeException e) {
                log.warn("本頁商品 {} 狀態更新失敗，繼續處理其他商品",id,e);
                String message=e instanceof IllegalArgumentException || e instanceof ProductStatusAccessException
                    ? e.getMessage() : "商品更新失敗，請重新整理後再試";
                results.add(new Item(id,null,null,"FAILED",message));failed++;
            }
        }
        return new Result(updated,skipped,failed,states,List.copyOf(results),List.copyOf(confirmations));
    }
}
