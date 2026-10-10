package com.fruitude.orders;
import com.fruitude.orders.model.*;
import com.fruitude.product.model.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class OrderInventoryServiceTest {
    private Orders order=new Orders();private int physical=20,pending=8,shipCalls=0,releaseCalls=0;
    @SuppressWarnings("unchecked") private <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
    private OrderInventoryService service(int inventory) {
        order.setOrdersId(1);order.setOrdersStatus(0);order.setInventoryState(inventory);
        var repo=proxy(OrdersRepository.class,(o,m,a)->m.getName().equals("lockForInventory")?Optional.of(order):order);
        var detail=new OrdersDetail();detail.setSkuId(2);detail.setOrdersQuantity(3);
        var details=proxy(OrdersDetailRepository.class,(o,m,a)->List.of(detail));
        var stock=proxy(SkuStockRepository.class,(o,m,a)->switch(m.getName()) {
            case "lockProducts","lockSkus" -> List.of(2);
            case "shipStock" -> {shipCalls++;int qty=(Integer)a[1];if(physical<qty||pending<qty)yield 0;physical-=qty;pending-=qty;yield 1;}
            case "releaseStock" -> {releaseCalls++;pending-=(Integer)a[1];yield 1;}
            default -> 0;
        });
        return new OrderInventoryService(repo,details,stock,new ProductLifecycleService(new ProductStatusAccess(null,null)));
    }
    @Test public void shippingDeductsPhysicalAndPendingExactlyOnce() {
        var svc=service(1);assertTrue(svc.changeStatus(1,1));assertTrue(svc.changeStatus(1,1));
        assertEquals(17,physical);assertEquals(5,pending);assertEquals(1,shipCalls);assertEquals(Integer.valueOf(2),order.getInventoryState());
        try{svc.changeStatus(1,0);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void cancellationReleasesOnlyPendingAndCannotBeReopened() {
        var svc=service(1);svc.changeStatus(1,4);svc.changeStatus(1,12);
        assertEquals(20,physical);assertEquals(5,pending);assertEquals(1,releaseCalls);assertEquals(0,shipCalls);
        try{svc.changeStatus(1,1);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void physicalShortageBlocksShippingAndDirectDeliveryCannotBypassIt() {
        var svc=service(1);physical=0;try{svc.changeStatus(1,1);fail();}catch(IllegalArgumentException expected){}
        assertEquals(Integer.valueOf(1),order.getInventoryState());assertEquals(Integer.valueOf(0),order.getOrdersStatus());
        try{svc.changeStatus(1,7);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void legacyOrdersNeverDeductPhysicalStockAgain() {
        service(0).changeStatus(1,1);assertEquals(20,physical);assertEquals(8,pending);assertEquals(0,shipCalls);
    }
}
