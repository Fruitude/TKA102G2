package com.fruitude.orders.model;
import java.util.*;
import com.fruitude.product.model.ProductLifecycleService;
import com.fruitude.utils.Utils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OrderInventoryService {
    private final OrdersRepository orders;
    private final OrdersDetailRepository details;
    private final SkuStockRepository stock;
    private final ProductLifecycleService lifecycle;
    public OrderInventoryService(OrdersRepository orders, OrdersDetailRepository details, SkuStockRepository stock, ProductLifecycleService lifecycle) {
        this.orders=orders;this.details=details;this.stock=stock;this.lifecycle=lifecycle;
    }
    @Transactional
    public boolean changeStatus(Integer id,Integer next) {
        if(next==null || Utils.OrderStatus.fromCode(next)==null)throw new IllegalArgumentException("訂單狀態不正確");
        var order=orders.lockForInventory(id).orElse(null);if(order==null)return false;
        if(Objects.equals(next,order.getOrdersStatus()))return true;
        int inventory=order.getInventoryState()==null?0:order.getInventoryState();
        if(inventory==1) {
            boolean shipping=next==1;
            boolean cancelling=Set.of(2,4,11,12).contains(next);
            if(next!=0&&!shipping&&!cancelling)throw new IllegalArgumentException("請先執行出貨，再變更配送或收件狀態");
            if(shipping||cancelling) {
                Map<Integer,Integer> qty=new TreeMap<>();
                for(var d:details.findByOrdersIdIn(List.of(id))) {
                    if(d.getSkuId()==null||d.getOrdersQuantity()==null||d.getOrdersQuantity()<=0)throw new IllegalArgumentException("訂單明細數量不正確");
                    qty.merge(d.getSkuId(),d.getOrdersQuantity(),Math::addExact);
                }
                if(qty.isEmpty())throw new IllegalArgumentException("訂單沒有可處理的商品明細");
                stock.lockProducts(qty.keySet());stock.lockSkus(qty.keySet());
                for(var entry:qty.entrySet()) {
                    int changed=shipping?stock.shipStock(entry.getKey(),entry.getValue()):stock.releaseStock(entry.getKey(),entry.getValue());
                    if(changed!=1)throw new IllegalArgumentException(shipping?"庫存不足或待出貨量不一致，請先確認進貨與庫存後再出貨":"待出貨量不一致，請先確認庫存");
                }
                order.setInventoryState(shipping?2:3);
                lifecycle.refreshSupplyStates(qty.keySet());stock.closeDepletedSkus(qty.keySet());stock.closeProductsWithoutListedSkus(qty.keySet());
                lifecycle.clearFrontCacheAfterCommit();
            }
        } else if(inventory==2 && next==0)throw new IllegalArgumentException("已出貨訂單不可改回待出貨，以免重複處理庫存");
        else if(inventory==3 && (next==0||next==1))throw new IllegalArgumentException("此訂單已取消並釋放待出貨量，請重新建立訂單");
        // 舊訂單 inventory_state=0 保留原處理方式，避免再次扣除已被舊程式扣掉的庫存。
        order.setOrdersStatus(next);orders.saveAndFlush(order);return true;
    }
}
