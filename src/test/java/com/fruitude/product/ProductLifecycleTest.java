package com.fruitude.product;
import com.fruitude.product.model.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProductLifecycleTest {
    ProductStatusAccess access(boolean admin){return new ProductStatusAccess(null,null){@Override public boolean canRestoreDiscontinued(){return admin;}};}
    Product product(int status,int...states){var p=new Product();p.setProductId(1);p.setStatus((byte)status);for(int x:states){var s=new ProductSku();s.setSkuId(p.getProductSkus().size()+1);s.setProduct(p);s.setStatus((byte)x);s.setStock(50);s.setSafetyStock(10);p.getProductSkus().add(s);}return p;}
    List<Byte> states(Product p){return p.getProductSkus().stream().map(ProductSku::getStatus).toList();}
    @Test public void productRetirementCascadesToStoppingOrPermanentWithoutRestoringPermanentSkus(){
        var l=new ProductLifecycleService(access(false));
        var p=product(1,0,1,2,3,4,5,6,7);
        for(var s:p.getProductSkus()){s.setStock(0);s.setOutboundQty(0);s.setInboundQty(0);}
        p.getProductSkus().get(0).setStock(10);
        p.getProductSkus().get(1).setInboundQty(2);
        p.getProductSkus().get(2).setStock(5);p.getProductSkus().get(2).setOutboundQty(5);
        p.getProductSkus().get(3).setStock(3);p.getProductSkus().get(3).setOutboundQty(4);p.getProductSkus().get(3).setInboundQty(1);
        p.getProductSkus().get(4).setStock(8);p.getProductSkus().get(4).setOutboundQty(3);
        p.getProductSkus().get(7).setStock(50);
        l.changeProduct(p,(byte)2,false);
        assertEquals(List.of((byte)6,(byte)6,(byte)7,(byte)6,(byte)6,(byte)7,(byte)7,(byte)7),states(p));
        assertEquals(Byte.valueOf((byte)2),p.getStatus());assertFalse(p.getAutoRestockEnabled());
        l.synchronize(p);assertEquals(Byte.valueOf((byte)2),p.getStatus());
        l.afterReceipt(p,Set.of(2));assertEquals(Byte.valueOf((byte)2),p.getStatus());
        try{l.changeProduct(p,(byte)1,false);fail();}catch(com.fruitude.product.model.ProductStatusAccess.ProductStatusAccessException expected){}
        new ProductLifecycleService(access(true)).changeProduct(p,(byte)1,false);
        assertEquals(Byte.valueOf((byte)1),p.getStatus());
        assertEquals(Byte.valueOf((byte)6),p.getProductSkus().get(0).getStatus());
        assertEquals(Byte.valueOf((byte)7),p.getProductSkus().get(7).getStatus());
    }
    @Test public void manualProductOfflineClosesSaleAndPreparedStatesAndBlocksReceiptWakeup(){
        var l=new ProductLifecycleService(access(false));var p=product(1,1,2,3,4,5,6,7);l.changeProduct(p,(byte)0,false);
        assertEquals(List.of((byte)0,(byte)0,(byte)0,(byte)4,(byte)0,(byte)6,(byte)7),states(p));
        assertFalse(p.getAutoRestockEnabled());l.afterReceipt(p,Set.of(4));assertEquals(Byte.valueOf((byte)0),p.getStatus());assertEquals(Byte.valueOf((byte)4),p.getProductSkus().get(3).getStatus());
    }
    @Test public void automaticSoldOutProductCanWakeAfterActualReceipt(){
        var l=new ProductLifecycleService(access(false));var p=product(1,1);var s=p.getProductSkus().get(0);s.setStock(0);s.setOutboundQty(10);l.synchronize(p);
        assertEquals(Byte.valueOf((byte)4),s.getStatus());assertEquals(Byte.valueOf((byte)0),p.getStatus());assertTrue(p.getAutoRestockEnabled());
        s.setStock(15);l.closeDepletedSkus(p);assertEquals(Byte.valueOf((byte)4),s.getStatus());
        l.afterReceipt(p,Set.of(1));assertEquals(Byte.valueOf((byte)3),s.getStatus());assertEquals(Byte.valueOf((byte)1),p.getStatus());
    }
    @Test public void preparedStateIsFiveAndRequiresProductOrActivityActivation(){
        var l=new ProductLifecycleService(access(false));var p=product(0,5,4,7);l.synchronize(p);assertEquals(Byte.valueOf((byte)0),p.getStatus());
        l.changeProduct(p,(byte)1,false);assertEquals(List.of((byte)1,(byte)4,(byte)7),states(p));
        try{l.validateSkuChange((byte)1,(byte)5);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void soldOutRestoreRequiresManualConfirmationAndRetirementRequiresAdmin(){
        var l=new ProductLifecycleService(access(false));
        try{l.validateSkuChange((byte)4,(byte)1);fail();}catch(com.fruitude.product.model.ProductLifecycleService.SkuRestockConfirmationException expected){}
        l.validateSkuChange((byte)4,(byte)1,true);l.validateSkuChange((byte)1,(byte)7);
        try{l.validateSkuChange((byte)7,(byte)1);fail();}catch(com.fruitude.product.model.ProductStatusAccess.ProductStatusAccessException expected){}
        new ProductLifecycleService(access(true)).validateSkuChange((byte)7,(byte)1);
    }
    @Test public void supplierRetirementRemainsSaleableUntilPhysicalStockEnds(){
        var l=new ProductLifecycleService(access(false));var p=product(1,6);var s=p.getProductSkus().get(0);s.setStock(0);s.setInboundQty(5);l.synchronize(p);assertEquals(Byte.valueOf((byte)6),s.getStatus());
        s.setInboundQty(0);l.synchronize(p);assertEquals(Byte.valueOf((byte)7),s.getStatus());assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }
    @Test public void discontinuedProductNeverRecoversFromReceipt(){
        var l=new ProductLifecycleService(access(false));var p=product(2,4);l.afterReceipt(p,Set.of(1));assertEquals(Byte.valueOf((byte)2),p.getStatus());assertEquals(Byte.valueOf((byte)4),p.getProductSkus().get(0).getStatus());
    }
    @Test public void preparedActivationImmediatelyCalculatesFinalSupplyState(){
        var l=new ProductLifecycleService(access(false));
        var empty=product(0,5);var sku=empty.getProductSkus().get(0);sku.setStock(0);sku.setMaxBackorderQty(0);
        l.changeProduct(empty,(byte)1,false);assertEquals(Byte.valueOf((byte)4),sku.getStatus());assertEquals(Byte.valueOf((byte)0),empty.getStatus());assertTrue(empty.getAutoRestockEnabled());
        var shortSupply=product(0,5);shortSupply.getProductSkus().get(0).setStock(5);
        l.changeProduct(shortSupply,(byte)1,false);assertEquals(Byte.valueOf((byte)3),shortSupply.getProductSkus().get(0).getStatus());
    }
    @Test public void stoppingSupplyCannotWakeProductButManualListingCanSellIt(){
        var l=new ProductLifecycleService(access(false));var p=product(0,6);p.setAutoRestockEnabled(false);
        l.synchronize(p);assertEquals(Byte.valueOf((byte)0),p.getStatus());
        l.afterReceipt(p,Set.of(1));assertEquals(Byte.valueOf((byte)0),p.getStatus());
        l.changeProduct(p,(byte)1,false);assertEquals(Byte.valueOf((byte)1),p.getStatus());assertEquals(Byte.valueOf((byte)6),p.getProductSkus().get(0).getStatus());
        l.changeProduct(p,(byte)0,false);assertEquals(Byte.valueOf((byte)0),p.getStatus());assertEquals(Byte.valueOf((byte)6),p.getProductSkus().get(0).getStatus());
        l.synchronize(p);assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }
    @Test public void manualOfflineDoesNotAdvanceEvenAnEmptyStoppingSupplySku(){
        var l=new ProductLifecycleService(access(false));var p=product(1,6);p.getProductSkus().get(0).setStock(0);
        l.changeProduct(p,(byte)0,false);assertEquals(Byte.valueOf((byte)6),p.getProductSkus().get(0).getStatus());
        l.synchronize(p);assertEquals(Byte.valueOf((byte)7),p.getProductSkus().get(0).getStatus());assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }
    @Test public void automaticOfflineCanRecoverFromStoppingSupplyButOnlyAdminCanRestoreSkuState(){
        var l=new ProductLifecycleService(access(false));var p=product(0,6);p.setAutoRestockEnabled(true);
        l.synchronize(p);assertEquals(Byte.valueOf((byte)1),p.getStatus());assertEquals(Byte.valueOf((byte)6),p.getProductSkus().get(0).getStatus());
        for(byte next:new byte[]{0,1,2,3,4})try{l.validateSkuChange((byte)6,next);fail();}catch(com.fruitude.product.model.ProductStatusAccess.ProductStatusAccessException expected){}
        l.validateSkuChange((byte)6,(byte)6);l.validateSkuChange((byte)6,(byte)7);
        new ProductLifecycleService(access(true)).validateSkuChange((byte)6,(byte)1);
    }
    @Test public void adminRestoringPermanentProductStillJudgesPreparedSupplyImmediately(){
        var l=new ProductLifecycleService(access(true));var p=product(2,5);var sku=p.getProductSkus().get(0);sku.setStock(0);sku.setMaxBackorderQty(0);
        l.changeProduct(p,(byte)1,false);assertEquals(Byte.valueOf((byte)4),sku.getStatus());assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }

    @Test public void newScheduledSkusStayPreparedEvenWhenTheProductIsListed() {
        var lifecycle=new ProductLifecycleService(access(false));
        var service=new ProductService();org.springframework.test.util.ReflectionTestUtils.setField(service,"lifecycle",lifecycle);
        var saves=new java.util.concurrent.atomic.AtomicInteger();
        var repository=(ProductRepository)java.lang.reflect.Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,method,args)->{saves.incrementAndGet();return args[0];});
        org.springframework.test.util.ReflectionTestUtils.setField(service,"repository",repository);
        for(int[] states:new int[][]{{5},{1,5}}) {
            var p=product(1,states);var prepared=p.getProductSkus().get(p.getProductSkus().size()-1);
            prepared.setDefaultListingTime(ProductService.earliestListingTime());
            service.addProduct(p,true);assertEquals(Byte.valueOf((byte)5),prepared.getStatus());
            assertEquals(Byte.valueOf(states.length==1?(byte)0:(byte)1),p.getStatus());
            assertEquals(ProductService.earliestListingTime(),prepared.getDefaultListingTime());
        }
        assertEquals(2,saves.get());
        var invalid=product(0,5);invalid.getProductSkus().get(0).setDefaultListingTime(ProductService.earliestListingTime().minusMinutes(1));
        try{service.addProduct(invalid,false);fail();}catch(IllegalArgumentException expected){}
        assertEquals(2,saves.get());
    }


    @Test public void listingWindowIncludesLastMinuteAndHandlesLeapYears() {
        var sku=new ProductSku();sku.setStatus((byte)5);
        var earliest=java.time.LocalDateTime.of(2026,10,17,0,0);
        sku.setDefaultListingTime(java.time.LocalDateTime.of(2027,10,16,23,59));assertNull(ProductService.listingTimeError(sku,earliest));
        sku.setDefaultListingTime(java.time.LocalDateTime.of(2027,10,17,0,0));assertNotNull(ProductService.listingTimeError(sku,earliest));
        earliest=java.time.LocalDateTime.of(2024,3,1,0,0);
        sku.setDefaultListingTime(java.time.LocalDateTime.of(2025,2,28,23,59));assertNull(ProductService.listingTimeError(sku,earliest));
        sku.setDefaultListingTime(java.time.LocalDateTime.of(2025,3,1,0,0));assertNotNull(ProductService.listingTimeError(sku,earliest));
    }


    @Test public void scheduledCreationStillSavesWhenTheOtherListedSkuHasNoStock() {
        var service=new ProductService();org.springframework.test.util.ReflectionTestUtils.setField(service,"lifecycle",new ProductLifecycleService(access(false)));
        var saved=new java.util.concurrent.atomic.AtomicReference<Product>();
        var repository=(ProductRepository)java.lang.reflect.Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,method,args)->{saved.set((Product)args[0]);return args[0];});
        org.springframework.test.util.ReflectionTestUtils.setField(service,"repository",repository);
        var p=product(1,1,5);p.getProductSkus().get(0).setStock(0);
        p.getProductSkus().get(1).setDefaultListingTime(ProductService.earliestListingTime());
        service.addProduct(p,false);
        assertSame(p,saved.get());assertEquals(List.of((byte)4,(byte)5),states(p));assertEquals(Byte.valueOf((byte)0),p.getStatus());
    }

}
