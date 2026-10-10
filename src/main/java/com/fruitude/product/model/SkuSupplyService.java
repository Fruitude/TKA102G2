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
    public static byte nextStatus(byte status, long stock, long incoming, long outgoing,
                                  int backorder, int addOn, BigDecimal yield) {
        BigDecimal supply = BigDecimal.valueOf(stock).add(BigDecimal.valueOf(incoming).multiply(yield));
        if (status==1 && supply.add(BigDecimal.valueOf(Math.max(0,backorder))).compareTo(BigDecimal.valueOf(outgoing))<0) return 2;
        if (status==2 && supply.compareTo(BigDecimal.valueOf(outgoing).add(BigDecimal.valueOf(Math.max(0,addOn))))>=0) return 1;
        return status;
    }
    public static byte nextStatus(ProductSku sku, BigDecimal yield) {
        return nextStatus(sku.getStatus(), zero(sku.getStock()),zero(sku.getInboundQty()),zero(sku.getOutboundQty()),
            sku.getMaxBackorderQty()==null?10:sku.getMaxBackorderQty(),sku.getPurchaseAddOnQty()==null?20:sku.getPurchaseAddOnQty(),yield);
    }
    private static long zero(Integer value) { return value==null?0L:value.longValue(); }

    // Call after locking the products and SKUs in the enclosing inventory transaction.
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public int refreshLocked(Collection<Integer> ids) {
        if (ids.isEmpty()) return 0;
        var rates=yields(ids);
        var rows=jdbc.queryForList("SELECT s.* FROM product_sku s JOIN product p ON p.product_id=s.product_id WHERE s.sku_id IN (:ids) AND p.status=1 AND s.status IN (1,2)",Map.of("ids",ids));
        int changed=0;
        for (var row:rows) {
            int id=((Number)row.get("sku_id")).intValue(); byte old=((Number)row.get("status")).byteValue();
            byte next=nextStatus(old,number(row,"stock",0),number(row,"inbound_qty",0),number(row,"outbound_qty",0),
                (int)number(row,"max_backorder_qty",10),(int)number(row,"purchase_add_on_qty",20),rates.getOrDefault(id,DEFAULT_YIELD));
            if(next!=old) changed+=jdbc.update("UPDATE product_sku SET status=:next,updated_at=CURRENT_TIMESTAMP WHERE sku_id=:id AND status=:old",Map.of("id",id,"old",old,"next",next));
        }
        return changed;
    }
    private static long number(Map<String,Object> row,String key,long fallback) {
        return row.get(key)==null?fallback:((Number)row.get(key)).longValue();
    }
}
