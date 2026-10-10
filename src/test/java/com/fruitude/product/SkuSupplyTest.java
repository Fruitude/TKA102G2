package com.fruitude.product;
import com.fruitude.product.model.SkuSupplyService;
import com.fruitude.product.model.SkuSupplyService.Receipt;
import java.math.BigDecimal;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SkuSupplyTest {
    void rate(String expected,List<Receipt> rows) { assertEquals(0,new BigDecimal(expected).compareTo(SkuSupplyService.weightedYield(rows))); }
    @Test public void emptyHistoryFallsBackButRealZeroDoesNot() {
        rate(".9",List.of()); rate("0",List.of(new Receipt(100,100)));
        rate(".9",List.of(new Receipt(0,0),new Receipt(10,11)));
    }
    @Test public void shortHistoryWeightsByArrivalQuantity() {
        rate(".91",List.of(new Receipt(10,9),new Receipt(90,0)));
    }
    @Test public void blendsThirtyAndRecentFiveAndCapsThirty() {
        var rows=new ArrayList<Receipt>();for(int i=0;i<5;i++)rows.add(new Receipt(100,10));
        for(int i=5;i<30;i++)rows.add(new Receipt(100,4));
        rows.add(new Receipt(10000,10000));rate(".935",rows);
    }
    @Test public void moreThanEightPercentagePointDropUsesFive() {
        var rows=new ArrayList<Receipt>();for(int i=0;i<5;i++)rows.add(new Receipt(100,20));
        for(int i=5;i<30;i++)rows.add(new Receipt(100,0));rate(".8",rows);
        rows.clear();for(int i=0;i<5;i++)rows.add(new Receipt(100,10));
        for(int i=5;i<30;i++)rows.add(new Receipt(100,0));rate(".9",rows);
    }
    @Test public void exactlyEightPercentagePointsStillBlends() {
        var rows=new ArrayList<Receipt>();for(int i=0;i<5;i++)rows.add(new Receipt(1000,100));
        for(int i=5;i<30;i++)rows.add(new Receipt(1000,4));rate(".956",rows);
    }
    @Test public void purchasesUseYieldBackorderAndFiveBoxShortageCap() {
        var r=new BigDecimal(".8");
        assertEquals(10,SkuSupplyService.quantityLimit(1,0,10,0,10,r));
        assertEquals(5,SkuSupplyService.quantityLimit(2,0,10,0,10,r));
        assertEquals(5,SkuSupplyService.quantityLimit(3,0,10,0,10,r));
        assertEquals(3,SkuSupplyService.quantityLimit(3,0,4,0,10,r));
        assertEquals(5,SkuSupplyService.quantityLimit(3,100,0,0,10,r));
        assertEquals(0,SkuSupplyService.quantityLimit(6,0,10,0,10,r));
        assertEquals(0,SkuSupplyService.quantityLimit(3,0,1,0,10,r));
    }
    @Test public void shortageAndRecoveryHaveHysteresisAndSoldOutHasPriority() {
        var r=new BigDecimal(".9");
        assertEquals(2,SkuSupplyService.nextStatus((byte)1,0,10,0,10,10,20,r,false));
        assertEquals(2,SkuSupplyService.nextStatus((byte)2,29,0,0,10,10,20,r,false));
        assertEquals(1,SkuSupplyService.nextStatus((byte)2,30,0,0,10,10,20,r,false));
        assertEquals(4,SkuSupplyService.nextStatus((byte)1,0,1,10,10,10,20,r,false));
        assertEquals(4,SkuSupplyService.nextStatus((byte)2,0,1,10,10,10,20,r,false));
    }
    @Test public void soldOutRestoresOnlyAfterActualReceiptAndToFinalEligibleState() {
        var r=new BigDecimal(".9");
        assertEquals(4,SkuSupplyService.nextStatus((byte)4,100,0,0,10,10,20,r,false));
        assertEquals(3,SkuSupplyService.nextStatus((byte)4,5,0,0,10,10,20,r,true));
        assertEquals(2,SkuSupplyService.nextStatus((byte)4,10,0,0,10,10,20,r,true));
        assertEquals(1,SkuSupplyService.nextStatus((byte)4,30,0,0,10,10,20,r,true));
        assertEquals(4,SkuSupplyService.nextStatus((byte)4,0,1,0,10,10,20,r,true));
    }
    @Test public void stoppingSupplySellsOnlyPhysicalStockAndWaitsForPendingDelivery() {
        var r=new BigDecimal(".9");
        assertEquals(2,SkuSupplyService.quantityLimit(6,5,100,3,100,r));
        assertEquals(6,SkuSupplyService.nextStatus((byte)6,0,10,0,10,10,20,r,false));
        assertEquals(7,SkuSupplyService.nextStatus((byte)6,0,0,0,10,10,20,r,false));
        for(byte s:new byte[]{0,5,7})assertEquals(s,SkuSupplyService.nextStatus(s,100,100,0,10,10,20,r,true));
    }
    @Test public void procurementIncludesEndingAndSoldOutButExcludesRetirement() {
        var sku=new com.fruitude.product.model.ProductSku();sku.setStock(0);sku.setSafetyStock(10);sku.setOutboundQty(20);
        var r=new BigDecimal(".9");
        assertEquals(Integer.valueOf(56),SkuSupplyService.suggestedPurchase(sku,r));
        assertNull(SkuSupplyService.suggestedPurchase(sku,BigDecimal.ZERO));
        for(byte s:new byte[]{1,2,3,4}){sku.setStatus(s);assertTrue(SkuSupplyService.needsPurchase(sku,r));}
        for(byte s:new byte[]{0,5,6,7}){sku.setStatus(s);assertFalse(SkuSupplyService.needsPurchase(sku,r));}
    }
}
