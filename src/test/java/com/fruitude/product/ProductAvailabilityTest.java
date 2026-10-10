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
        for(int state:new int[]{5,5,4,7,0}){var s=new ProductSku();s.setSkuId(p.getProductSkus().size()+1);s.setStatus((byte)state);s.setProduct(p);p.getProductSkus().add(s);}
        var repo=proxy(ProductRepository.class,(o,m,a)->m.getName().equals("lockForStatus")?Optional.of(p):p);
        var skus=proxy(ProductSkuRepository.class,(o,m,a)->List.of(1,3,4,5));
        var svc=new ProductAvailabilityService(repo,skus,new ProductLifecycleService(new ProductStatusAccess(null,null)));
        assertEquals(1,svc.refresh(1,LocalDateTime.now()));
        assertEquals(List.of((byte)1,(byte)5,(byte)4,(byte)7,(byte)0),p.getProductSkus().stream().map(ProductSku::getStatus).toList());
        assertEquals(Byte.valueOf((byte)1),p.getStatus());assertEquals(0,svc.refresh(1,LocalDateTime.now()));
        p.setStatus((byte)2);assertEquals(0,svc.refresh(1,LocalDateTime.now()));assertEquals(Byte.valueOf((byte)2),p.getStatus());
    }
}
