package com.fruitude.product;
import com.fruitude.product.model.ProductSku;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class ProductSkuStockStatusesTest {
    private ProductSku sku(int stock,int safety,int inbound,int outbound) {
        var s=new ProductSku();s.setStock(stock);s.setSafetyStock(safety);s.setInboundQty(inbound);s.setOutboundQty(outbound);return s;
    }
    @Test public void normalAndSafetyUseExpectedStockAndLowStockRequiresUnsetSafety() {
        var s=sku(50,10,20,5);assertEquals(List.of("正常"),s.getStockStatuses());assertFalse(s.isStockAbnormal());
        s=sku(5,10,5,0);assertEquals(List.of("正常"),s.getStockStatuses());
        s.setOutboundQty(1);assertEquals(List.of("低於安全庫存"),s.getStockStatuses());assertTrue(s.isStockAbnormal());
        s=sku(5,0,0,0);assertEquals(List.of("庫存低於10","未設安全庫存"),s.getStockStatuses());
    }
    @Test public void multipleAlertsAndExclusiveInboundOutboundThresholds() {
        var s=sku(101,200,101,101);
        assertEquals(List.of("低於安全庫存","庫存高於100","待進貨超過100","待出貨超過100"),s.getStockStatuses());
        s=sku(100,10,100,100);assertEquals(List.of("待進貨超過50","待出貨超過50"),s.getStockStatuses());
        s=sku(100,10,50,50);assertEquals(List.of("正常"),s.getStockStatuses());
    }
    @Test public void nullsAndLargeQuantitiesDoNotOverflow() {
        var s=new ProductSku();s.setStock(null);s.setSafetyStock(null);s.setInboundQty(null);s.setOutboundQty(null);
        assertEquals(List.of("庫存低於10","未設安全庫存"),s.getStockStatuses());
        s=sku(Integer.MAX_VALUE,10,Integer.MAX_VALUE,0);assertFalse(s.getStockStatuses().contains("低於安全庫存"));
    }
}
