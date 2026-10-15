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
                SELECT valid.*, ROW_NUMBER() OVER (
                    PARTITION BY sku_id ORDER BY sample_date DESC, po_id DESC, detail_id DESC) AS rn
                FROM (
                    SELECT d.sku_id, po.id AS po_id, d.id AS detail_id,
                        CASE WHEN po.inbound_status IN (2,3)
                            THEN d.quantity ELSE d.arrived_pcs END AS arrived_pcs,
                        CASE WHEN po.inbound_status IN (2,3)
                            THEN d.quantity ELSE d.defect_pcs END AS defect_pcs,
                        CASE WHEN po.inbound_status=3
                            THEN COALESCE(po.inbound_date,po.order_date) ELSE po.inbound_date END AS sample_date
                    FROM purchaseorder_details d JOIN purchaseorder po ON po.id=d.po_id
                    JOIN product_sku s ON s.sku_id=d.sku_id JOIN product p ON p.product_id=s.product_id
                    WHERE d.sku_id IN (:ids) AND po.vendor_id=p.vendor_id
                        AND (po.po_status IN (1,4) AND po.inbound_status IN (1,2,3))
                ) valid
                WHERE sample_date>=:since AND sample_date<=:now
                    AND arrived_pcs>0 AND defect_pcs>=0 AND defect_pcs<=arrived_pcs
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
        // Backorder allowance does not reopen an exhausted supply pool.
        if (status!=6 && boxes(a)==0) return 0;
        return switch(status) {
            case 1 -> Math.min(10,boxes(a.add(BigDecimal.valueOf(Math.max(0,backorder)))));
            case 2 -> Math.min(5,boxes(a.add(BigDecimal.valueOf(Math.max(0,backorder)))));
            case 3 -> Math.min(5,boxes(a));
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
            if(c<=5) return 3;
            if(a.compareTo(safe)>=0) return (byte)(a.compareTo(target)>=0?1:2);
            return 3;
        }
        if(status==1 || status==2) {
            if(c<=5) return (byte)(c==0?4:3);
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
    public int refreshLocked(Collection<Integer> ids) { return refreshLocked(ids, false); }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public int refreshLocked(Collection<Integer> ids, boolean allowSoldOutRecovery) {
        if(ids.isEmpty())return 0;
        var rates=yields(ids);
        String eligibility=allowSoldOutRecovery
            ? "((p.status<>2 AND (p.status=1 OR p.auto_restock_enabled=1) AND s.status IN (1,2,3,4,6)) OR (p.status=2 AND s.status=6))"
            : "((p.status=1 AND s.status IN (1,2,3,6)) OR (p.status=2 AND s.status=6))";
        var rows=jdbc.queryForList("SELECT s.* FROM product_sku s JOIN product p ON p.product_id=s.product_id WHERE s.sku_id IN (:ids) AND "+eligibility,Map.of("ids",ids));
        int changed=0;
        for(var row:rows) {
            int id=((Number)row.get("sku_id")).intValue();byte old=((Number)row.get("status")).byteValue();
            byte next=nextStatus(old,number(row,"stock",0),number(row,"inbound_qty",0),number(row,"outbound_qty",0),
                (int)number(row,"safety_stock",0),(int)number(row,"max_backorder_qty",10),(int)number(row,"purchase_add_on_qty",20),
                rates.getOrDefault(id,DEFAULT_YIELD),allowSoldOutRecovery);
            if(next!=old)changed+=jdbc.update("UPDATE product_sku SET status=:next,updated_at=CURRENT_TIMESTAMP WHERE sku_id=:id AND status=:old",Map.of("id",id,"old",old,"next",next));
        }
        return changed;
    }
    private static final String HISTORY_TOKEN="""
        (SELECT COALESCE(SHA2(CAST(JSON_OBJECTAGG(CAST(d.id AS CHAR),
            JSON_ARRAY(po.id,po.po_status,po.inbound_status,po.inbound_date,po.order_date,
                       d.quantity,d.arrived_pcs,d.defect_pcs)) AS CHAR),256),'')
         FROM purchaseorder_details d JOIN purchaseorder po ON po.id=d.po_id
         WHERE d.sku_id=s.sku_id AND po.vendor_id=p.vendor_id)
        """;

    /** Post-commit reconciliation: no preemptive product/SKU locks, only guarded writes. */
    @org.springframework.transaction.annotation.Transactional(
        propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
        isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void refreshChangedProducts(Collection<Integer> productIds, Set<Integer> recoverable) {
        for(var productId:new TreeSet<>(productIds)) {
            boolean complete=false;
            for(int attempt=0;attempt<4 && !complete;attempt++) {
                var now=LocalDateTime.now(ZoneId.of("Asia/Taipei"));
                var rows=jdbc.queryForList("SELECT s.*,p.status AS parent_status,p.auto_restock_enabled AS parent_auto,"
                    +"p.updated_at AS parent_updated,p.vendor_id AS parent_vendor,"+HISTORY_TOKEN+" AS history_token,"
                    +duePromotion()+" AS due FROM product_sku s JOIN product p ON p.product_id=s.product_id WHERE p.product_id=:pid ORDER BY s.sku_id",
                    Map.of("pid",productId,"now",now));
                var rates=yields(rows.stream().map(r->((Number)r.get("sku_id")).intValue()).toList());
                boolean stale=false,activated=false;
                for(var row:rows) {
                    int id=(int)number(row,"sku_id",0), parent=(int)number(row,"parent_status",0);
                    byte old=(byte)number(row,"status",0);
                    boolean auto=truth(row.get("parent_auto"));
                    boolean due=old==5 && parent!=2 && truth(row.get("due"));
                    if(!due && old!=6 && (parent==2 || (parent==0 && !auto))) continue;
                    boolean recovery=recoverable.contains(id) && auto && parent!=2;
                    byte next=nextStatus(due?(byte)1:old,number(row,"stock",0),number(row,"inbound_qty",0),
                        number(row,"outbound_qty",0),(int)number(row,"safety_stock",0),
                        (int)number(row,"max_backorder_qty",10),(int)number(row,"purchase_add_on_qty",20),
                        rates.getOrDefault(id,DEFAULT_YIELD),recovery);
                    if(next==old) continue;
                    var args=new HashMap<String,Object>();
                    args.put("id",id); args.put("next",next); args.put("now",now);
                    StringBuilder guards=new StringBuilder("s.sku_id=:id");
                    for(var column:List.of("status","stock","inbound_qty","outbound_qty","safety_stock",
                            "max_backorder_qty","purchase_add_on_qty","price","updated_at","default_listing_time")) {
                        guards.append(" AND s.").append(column).append(" <=> :").append(column);
                        args.put(column,row.get(column));
                    }
                    args.put("parent_status",row.get("parent_status"));args.put("parent_auto",row.get("parent_auto"));
                    args.put("parent_updated",row.get("parent_updated"));args.put("parent_vendor",row.get("parent_vendor"));
                    args.put("history",row.get("history_token"));
                    guards.append(" AND p.status <=> :parent_status AND p.auto_restock_enabled <=> :parent_auto")
                        .append(" AND p.updated_at <=> :parent_updated AND p.vendor_id <=> :parent_vendor")
                        .append(" AND ").append(HISTORY_TOKEN).append(" <=> :history");
                    if(due) guards.append(" AND ").append(duePromotion());
                    int changed=jdbc.update("UPDATE product_sku s JOIN product p ON p.product_id=s.product_id "
                        +"SET s.status=:next,s.updated_at=CURRENT_TIMESTAMP WHERE "+guards,args);
                    if(changed==0) { stale=true;break; }
                    activated|=due && next>=1 && next<=3;
                }
                if(!stale) complete=synchronizeParent(productId,activated);
            }
            if(!complete) throw new IllegalStateException("商品 "+productId+" 數量持續異動，稍後重試");
        }
    }
    private static boolean truth(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue()!=0);
    }
    private static String duePromotion() {
        return "((s.default_listing_time IS NOT NULL AND s.default_listing_time<=:now) OR (s.default_listing_time IS NULL AND EXISTS(SELECT 1 FROM promotion pr JOIN promo_project a ON a.promo_project_id=pr.promo_project_id "
            +"WHERE pr.sku_id=s.sku_id AND a.status=1 AND a.promo_type='SKU' "
            +"AND a.promo_project_start<=:now AND (a.promo_project_end IS NULL OR a.promo_project_end>:now))))";
    }
    private boolean synchronizeParent(int id,boolean activated) {
        String listed="EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.product_id AND s.status IN(1,2,3,6) AND s.price>0)";
        var rows=jdbc.queryForList("SELECT p.status,p.auto_restock_enabled,p.updated_at,"+listed+" AS listed FROM product p WHERE p.product_id=:id",Map.of("id",id));
        if(rows.isEmpty())return true;
        var row=rows.get(0);int old=(int)number(row,"status",0);
        boolean auto=truth(row.get("auto_restock_enabled"));
        if(old==2 || (old==0 && !auto && !activated))return true;
        int next=truth(row.get("listed"))?1:0;
        if(old==next && (!activated || auto))return true;
        var args=new HashMap<String,Object>();
        args.put("id",id);args.put("old",old);args.put("next",next);
        args.put("auto",row.get("auto_restock_enabled"));args.put("updated",row.get("updated_at"));
        args.put("newAuto",auto||activated);
        return jdbc.update("UPDATE product p SET p.status=:next,p.auto_restock_enabled=:newAuto,p.updated_at=CURRENT_TIMESTAMP "
            +"WHERE p.product_id=:id AND p.status=:old AND p.auto_restock_enabled <=> :auto AND p.updated_at <=> :updated "
            +"AND :next=CASE WHEN "+listed+" THEN 1 ELSE 0 END",args)>0;
    }
    private static long number(Map<String,Object> row,String key,long fallback) {
        return row.get(key)==null?fallback:((Number)row.get(key)).longValue();
    }
}
