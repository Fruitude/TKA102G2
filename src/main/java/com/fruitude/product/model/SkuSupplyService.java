package com.fruitude.product.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Shared supply calculation for admin changes, availability jobs and order inventory transactions. */
@Service
public class SkuSupplyService {
    public static final BigDecimal DEFAULT_YIELD = new BigDecimal("0.90");
    public record Receipt(long arrived, long defect) {}
    private final NamedParameterJdbcTemplate jdbc;
    public SkuSupplyService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<Integer, BigDecimal> yields(Collection<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        var now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        String sql = """
            SELECT sku_id, arrived_pcs, defect_pcs FROM (
                SELECT d.sku_id, d.arrived_pcs, d.defect_pcs,
                  ROW_NUMBER() OVER (PARTITION BY d.sku_id ORDER BY po.inbound_date DESC, po.id DESC, d.id DESC) AS rn
                FROM purchaseorder_details d JOIN purchaseorder po ON po.id=d.po_id
                JOIN product_sku s ON s.sku_id=d.sku_id JOIN product p ON p.product_id=s.product_id
                WHERE d.sku_id IN (:ids) AND po.vendor_id=p.vendor_id
                  AND po.po_status=1 AND po.inbound_status IN (1,2)
                  AND po.inbound_date>=:since AND po.inbound_date<=:now
                  AND d.arrived_pcs>0 AND d.defect_pcs>=0 AND d.defect_pcs<=d.arrived_pcs
            ) receipts WHERE rn<=30 ORDER BY sku_id,rn
            """;
        Map<Integer, List<Receipt>> history = new HashMap<>();
        jdbc.query(sql, Map.of("ids",ids,"since",now.minusDays(90),"now",now), rs -> {
            history.computeIfAbsent(rs.getInt("sku_id"), k->new ArrayList<>())
                .add(new Receipt(rs.getLong("arrived_pcs"),rs.getLong("defect_pcs")));
        });
        Map<Integer, BigDecimal> result = new HashMap<>();
        ids.forEach(id->result.put(id, weightedYield(history.getOrDefault(id,List.of()))));
        return result;
    }

    // Receipts must be newest first. Zero yield is real data, not a missing-history fallback.
    public static BigDecimal weightedYield(List<Receipt> receipts) {
        var valid = receipts.stream().filter(x->x.arrived()>0 && x.defect()>=0 && x.defect()<=x.arrived()).limit(30).toList();
        if (valid.isEmpty()) return DEFAULT_YIELD;
        BigDecimal r30 = ratio(valid), r5 = ratio(valid.subList(0,Math.min(5,valid.size())));
        if (r30.subtract(r5).compareTo(new BigDecimal("0.08"))>0) return r5;
        return r30.multiply(new BigDecimal("0.7")).add(r5.multiply(new BigDecimal("0.3")));
    }
    private static BigDecimal ratio(List<Receipt> receipts) {
        long arrived=0,good=0;
        for (var receipt:receipts) { arrived+=receipt.arrived();good+=receipt.arrived()-receipt.defect(); }
        return BigDecimal.valueOf(good).divide(BigDecimal.valueOf(arrived),MathContext.DECIMAL128);
    }
    public static BigDecimal remaining(long stock,long incoming,long outgoing,BigDecimal yield) {
        return BigDecimal.valueOf(stock).add(BigDecimal.valueOf(incoming).multiply(yield)).subtract(BigDecimal.valueOf(outgoing));
    }
    public static int boxes(BigDecimal amount) {
        return amount.max(BigDecimal.ZERO).min(BigDecimal.valueOf(Integer.MAX_VALUE))
            .setScale(0,java.math.RoundingMode.FLOOR).intValue();
    }
    public static int quantityLimit(int status,long stock,long incoming,long outgoing,int backorder,BigDecimal yield) {
        BigDecimal a=remaining(stock,incoming,outgoing,yield);
        return switch(status) {
            case 1 -> Math.min(10,boxes(a.add(BigDecimal.valueOf(Math.max(0,backorder)))));
            case 2 -> Math.min(5,boxes(a.add(BigDecimal.valueOf(Math.max(0,backorder)))));
            case 3 -> Math.min(10,boxes(a));
            case 6 -> Math.min(10,boxes(BigDecimal.valueOf(stock-outgoing)));
            default -> 0;
        };
    }
    public static byte nextStatus(byte status,long stock,long incoming,long outgoing,int safety,
                                  int backorder,int addOn,BigDecimal yield,boolean actualReceipt) {
        BigDecimal a=remaining(stock,incoming,outgoing,yield);
        int c=boxes(a),b=boxes(a.add(BigDecimal.valueOf(Math.max(0,backorder))));
        BigDecimal safe=BigDecimal.valueOf(Math.max(0,safety));
        BigDecimal target=safe.add(BigDecimal.valueOf(Math.max(0,addOn)));
        if(status==6) return (byte)(incoming==0 && stock-outgoing<=0?7:6);
        if(status==4) {
            if(!actualReceipt || c==0) return 4;
            status=3;
        }
        if(status==3) {
            if(c==0) return 4;
            if(a.compareTo(safe)>=0) return (byte)(a.compareTo(target)>=0?1:2);
            return 3;
        }
        if(status==1 || status==2) {
            if(b==0) return 4;
            if(status==1 && a.compareTo(safe)<0) return 2;
            if(status==2 && a.compareTo(target)>=0) return 1;
        }
        return status;
    }
    public static byte nextStatus(ProductSku sku,BigDecimal yield,boolean actualReceipt) {
        return nextStatus(sku.getStatus(),zero(sku.getStock()),zero(sku.getInboundQty()),zero(sku.getOutboundQty()),
            (int)zero(sku.getSafetyStock()),sku.getMaxBackorderQty()==null?10:sku.getMaxBackorderQty(),
            sku.getPurchaseAddOnQty()==null?20:sku.getPurchaseAddOnQty(),yield,actualReceipt);
    }
    private static long zero(Integer value) { return value==null?0L:value.longValue(); }
    public static boolean needsPurchase(ProductSku sku,BigDecimal yield) {
        return sku.getStatus()!=null && sku.getStatus()>=1 && sku.getStatus()<=4
            && remaining(zero(sku.getStock()),zero(sku.getInboundQty()),zero(sku.getOutboundQty()),yield)
                .compareTo(BigDecimal.valueOf(zero(sku.getSafetyStock())))<0;
    }
    public static Integer suggestedPurchase(ProductSku sku,BigDecimal yield) {
        if(yield.signum()==0) return null; // Manual supplier review; never divide by zero.
        var deficit=BigDecimal.valueOf(zero(sku.getSafetyStock())+(sku.getPurchaseAddOnQty()==null?20:sku.getPurchaseAddOnQty()))
            .subtract(remaining(zero(sku.getStock()),zero(sku.getInboundQty()),zero(sku.getOutboundQty()),yield)).max(BigDecimal.ZERO);
        return deficit.divide(yield,0,java.math.RoundingMode.CEILING).min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public int refreshLocked(Collection<Integer> ids) {
        if(ids.isEmpty())return 0;
        var rates=yields(ids);
        var rows=jdbc.queryForList("SELECT s.* FROM product_sku s JOIN product p ON p.product_id=s.product_id WHERE s.sku_id IN (:ids) AND p.status=1 AND s.status IN (1,2,3,6)",Map.of("ids",ids));
        int changed=0;
        for(var row:rows) {
            int id=((Number)row.get("sku_id")).intValue();byte old=((Number)row.get("status")).byteValue();
            byte next=nextStatus(old,number(row,"stock",0),number(row,"inbound_qty",0),number(row,"outbound_qty",0),
                (int)number(row,"safety_stock",0),(int)number(row,"max_backorder_qty",10),(int)number(row,"purchase_add_on_qty",20),
                rates.getOrDefault(id,DEFAULT_YIELD),false);
            if(next!=old)changed+=jdbc.update("UPDATE product_sku SET status=:next,updated_at=CURRENT_TIMESTAMP WHERE sku_id=:id AND status=:old",Map.of("id",id,"old",old,"next",next));
        }
        return changed;
    }
    private static long number(Map<String,Object> row,String key,long fallback) {
        return row.get(key)==null?fallback:((Number)row.get(key)).longValue();
    }
}
