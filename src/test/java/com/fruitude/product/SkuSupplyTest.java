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
    @Test public void thresholdsAreStrictForShortageAndInclusiveForRecovery() {
        var yield=new BigDecimal(".8");
        assertEquals(1,SkuSupplyService.nextStatus((byte)1,20,10,38,10,20,yield));
        assertEquals(2,SkuSupplyService.nextStatus((byte)1,20,10,39,10,20,yield));
        assertEquals(2,SkuSupplyService.nextStatus((byte)2,50,10,39,10,20,yield));
        assertEquals(1,SkuSupplyService.nextStatus((byte)2,51,10,39,10,20,yield));
        for(byte state:new byte[]{0,3,4,5,6}) assertEquals(state,SkuSupplyService.nextStatus(state,1000,1000,0,10,20,yield));
    }
}
