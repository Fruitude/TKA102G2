package com.fruitude.product;
import com.fruitude.product.model.*;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProductAvailabilityTest {
    @SuppressWarnings("unchecked") private <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
    @Test public void onlyDuePreparedSkuIsActivatedAndSoldOutRetiredFutureSkusStayUntouched() {
        var p=new Product();p.setProductId(1);p.setStatus((byte)0);
        for(int state:new int[]{5,5,4,7,0}){var s=new ProductSku();s.setSkuId(p.getProductSkus().size()+1);s.setStatus((byte)state);s.setStock(50);s.setProduct(p);p.getProductSkus().add(s);}
        var repo=proxy(ProductRepository.class,(o,m,a)->m.getName().equals("lockForStatus")?Optional.of(p):p);
        var skus=proxy(ProductSkuRepository.class,(o,m,a)->List.of(1,3,4,5));
        var svc=new ProductAvailabilityService(repo,skus,new ProductLifecycleService(new ProductStatusAccess(null,null)));
        assertEquals(1,svc.refresh(1,LocalDateTime.now()));
        assertEquals(List.of((byte)1,(byte)5,(byte)4,(byte)7,(byte)0),p.getProductSkus().stream().map(ProductSku::getStatus).toList());
        assertEquals(Byte.valueOf((byte)1),p.getStatus());assertEquals(0,svc.refresh(1,LocalDateTime.now()));
        p.setStatus((byte)2);assertEquals(0,svc.refresh(1,LocalDateTime.now()));assertEquals(Byte.valueOf((byte)2),p.getStatus());
    }
    @Test public void retiredProductFinishesSkuRetirementWithoutActivatingPreparedOrRelisting(){
        var p=new Product();p.setProductId(1);p.setStatus((byte)2);p.setAutoRestockEnabled(false);
        var s=new ProductSku();s.setSkuId(1);s.setStatus((byte)6);s.setStock(0);s.setInboundQty(2);s.setProduct(p);p.getProductSkus().add(s);
        var prepared=new ProductSku();prepared.setSkuId(2);prepared.setStatus((byte)5);prepared.setStock(10);prepared.setProduct(p);p.getProductSkus().add(prepared);
        var repo=proxy(ProductRepository.class,(o,m,a)->m.getName().equals("lockForStatus")?Optional.of(p):p);
        var skus=proxy(ProductSkuRepository.class,(o,m,a)->{throw new AssertionError("Retired product cannot activate promotions");});
        var svc=new ProductAvailabilityService(repo,skus,new ProductLifecycleService(new ProductStatusAccess(null,null)));
        assertEquals(0,svc.refresh(1,LocalDateTime.now()));assertEquals(Byte.valueOf((byte)6),s.getStatus());
        s.setInboundQty(0);assertEquals(0,svc.refresh(1,LocalDateTime.now()));assertEquals(Byte.valueOf((byte)7),s.getStatus());
        assertEquals(Byte.valueOf((byte)2),p.getStatus());assertEquals(Byte.valueOf((byte)5),prepared.getStatus());assertFalse(p.getAutoRestockEnabled());
    }
    @Test public void unchangedStoppingSkuCanRecoverAutomaticOfflineButNotManualOffline(){
        var p=new Product();p.setProductId(1);p.setStatus((byte)0);p.setAutoRestockEnabled(true);
        var s=new ProductSku();s.setSkuId(2);s.setStatus((byte)6);s.setStock(10);s.setProduct(p);p.getProductSkus().add(s);
        var repo=proxy(ProductRepository.class,(o,m,a)->m.getName().equals("lockForStatus")?Optional.of(p):p);
        var skus=proxy(ProductSkuRepository.class,(o,m,a)->List.of());var lifecycle=new ProductLifecycleService(new ProductStatusAccess(null,null));
        var svc=new ProductAvailabilityService(repo,skus,lifecycle);
        svc.refresh(1,LocalDateTime.now());assertEquals(Byte.valueOf((byte)1),p.getStatus());
        lifecycle.changeProduct(p,(byte)0,false);svc.refresh(1,LocalDateTime.now());assertEquals(Byte.valueOf((byte)0),p.getStatus());assertEquals(Byte.valueOf((byte)6),s.getStatus());
    }
}
