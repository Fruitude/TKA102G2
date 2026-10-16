package com.fruitude.product;

import com.fruitude.product.model.SkuSupplyService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.Assert.*;

/** Exercises the current post-commit reconciler, rather than the removed polling implementation. */
public class ProductAvailabilityTest {
    static class Inventory extends NamedParameterJdbcTemplate {
        int parentStatus=0, writes=0;
        boolean auto=true, conflictOnce=false;
        final List<Map<String,Object>> skus=new ArrayList<>();
        Inventory(int... states) {
            super(new DriverManagerDataSource());
            for(int state:states) {
                var row=new HashMap<String,Object>();
                row.put("sku_id",skus.size()+1);row.put("status",state);row.put("stock",50);
                row.put("inbound_qty",0);row.put("outbound_qty",0);row.put("safety_stock",0);
                row.put("max_backorder_qty",10);row.put("purchase_add_on_qty",20);row.put("price",100);
                row.put("history_token","");row.put("due",false);skus.add(row);
            }
        }
        @Override public List<Map<String,Object>> queryForList(String sql,Map<String,?> args) {
            assertFalse("Reconciliation must not take outer inventory locks",sql.contains("FOR UPDATE"));
            if(sql.startsWith("SELECT s.*"))return skus.stream().map(row->{
                var copy=new HashMap<>(row);copy.put("parent_status",parentStatus);copy.put("parent_auto",auto);
                if(row.get("default_listing_time") instanceof java.time.LocalDateTime time) {
                    assertTrue(sql.contains("s.default_listing_time<=:now"));
                    copy.put("due",!time.isAfter((java.time.LocalDateTime)args.get("now")));
                }
                copy.put("parent_vendor",1);return (Map<String,Object>)copy;
            }).toList();
            if(sql.startsWith("SELECT p.status"))return List.of(Map.of("status",parentStatus,"auto_restock_enabled",auto,
                "listed",skus.stream().anyMatch(row->Set.of(1,2,3,6).contains(row.get("status")))));
            throw new AssertionError(sql);
        }
        @Override public int update(String sql,Map<String,?> args) {
            writes++;
            if(sql.startsWith("UPDATE product_sku")) {
                var row=skus.get(((Number)args.get("id")).intValue()-1);
                if(conflictOnce){conflictOnce=false;row.put("stock",0);return 0;}
                row.put("status",((Number)args.get("next")).intValue());
            } else if(sql.startsWith("UPDATE product p")) {
                parentStatus=((Number)args.get("next")).intValue();auto=(Boolean)args.get("newAuto");
            } else throw new AssertionError(sql);
            return 1;
        }
        SkuSupplyService service(){return new SkuSupplyService(this){
            @Override public Map<Integer,BigDecimal> yields(Collection<Integer> ids){return Map.of();}
        };}
        List<Integer> states(){return skus.stream().map(row->((Number)row.get("status")).intValue()).toList();}
    }
    @Test public void onlyDuePreparedSkuActivatesAndUnchangedResultDoesNotWriteAgain() {
        var db=new Inventory(5,5,4,7,0);db.skus.get(0).put("due",true);
        var service=db.service();service.refreshChangedProducts(List.of(1),Set.of());
        assertEquals(List.of(1,5,4,7,0),db.states());assertEquals(1,db.parentStatus);
        int writes=db.writes;service.refreshChangedProducts(List.of(1),Set.of());assertEquals(writes,db.writes);
    }
    @Test public void retiredProductFinishesRetirementWithoutActivatingPreparedOrRelisting() {
        var db=new Inventory(6,5);db.parentStatus=2;db.auto=false;
        db.skus.get(0).put("stock",0);db.skus.get(0).put("inbound_qty",2);db.skus.get(1).put("due",true);
        var service=db.service();service.refreshChangedProducts(List.of(1),Set.of(1));assertEquals(List.of(6,5),db.states());
        db.skus.get(0).put("inbound_qty",0);service.refreshChangedProducts(List.of(1),Set.of(1));
        assertEquals(List.of(7,5),db.states());assertEquals(2,db.parentStatus);assertFalse(db.auto);
    }
    @Test public void automaticOfflineRecoversFromStoppingSupplyButManualOfflineStaysOffline() {
        var db=new Inventory(6);var service=db.service();service.refreshChangedProducts(List.of(1),Set.of());
        assertEquals(1,db.parentStatus);db.parentStatus=0;db.auto=false;
        service.refreshChangedProducts(List.of(1),Set.of(1));assertEquals(0,db.parentStatus);assertEquals(List.of(6),db.states());
    }
    @Test public void changedInventoryRecalculatesBeforeActivatingPreparedSku() {
        var db=new Inventory(5);db.skus.get(0).put("due",true);db.conflictOnce=true;
        db.service().refreshChangedProducts(List.of(1),Set.of());
        assertEquals(List.of(4),db.states());assertEquals(0,db.parentStatus);
    }

    @Test public void explicitListingTimeOverridesEarlierPromotionAndActivatesOnlyAfterDue() {
        var db=new Inventory(5);db.auto=false;db.skus.get(0).put("due",true);
        db.skus.get(0).put("default_listing_time",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei")).plusDays(1));
        var service=db.service();service.refreshChangedProducts(List.of(1),Set.of());
        assertEquals(List.of(5),db.states());assertEquals(0,db.parentStatus);assertEquals(0,db.writes);
        db.skus.get(0).put("default_listing_time",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei")).minusMinutes(1));
        service.refreshChangedProducts(List.of(1),Set.of());assertEquals(List.of(1),db.states());assertEquals(1,db.parentStatus);
    }
    @Test public void scheduledTimeCannotUndoManualOfflineOrPermanentRetirement() {
        for(int parent:new int[]{0,2}) {
            var db=new Inventory(parent==0?0:5);db.parentStatus=parent;db.auto=false;
            db.skus.get(0).put("default_listing_time",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei")).minusDays(1));
            db.service().refreshChangedProducts(List.of(1),Set.of());
            assertEquals(parent,db.parentStatus);assertEquals(List.of(parent==0?0:5),db.states());assertEquals(0,db.writes);
        }
    }

}
