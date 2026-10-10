package com.fruitude.orders;
import com.fruitude.orders.model.*;
import com.fruitude.product.model.*;
import com.fruitude.promo.model.MemberPromoUsageRepository;
import java.lang.reflect.Proxy;import java.util.*;
import org.junit.Test;import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
public class OrderSupplyEventsTest {
 @SuppressWarnings("unchecked") <T>T fake(Class<T> c,java.util.function.BiFunction<String,Object[],Object> f){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(p,m,a)->f.apply(m.getName(),a));}
 OrdersService service(Orders order,List<String> events){
  var svc=new OrdersService();var detail=new OrdersDetail();detail.setSkuId(1);detail.setOrdersQuantity(2);
  ReflectionTestUtils.setField(svc,"ordersRepository",fake(OrdersRepository.class,(n,a)->n.equals("findByIdForUpdate")?Optional.of(order):a[0]));
  ReflectionTestUtils.setField(svc,"ordersDetailRepository",fake(OrdersDetailRepository.class,(n,a)->List.of(detail)));
  ReflectionTestUtils.setField(svc,"memberPromoUsageRepository",fake(MemberPromoUsageRepository.class,(n,a)->0));
  ReflectionTestUtils.setField(svc,"frontCatalogService",new FrontCatalogService(null));
  ReflectionTestUtils.setField(svc,"skuStockRepository",fake(SkuStockRepository.class,(n,a)->{
   events.add(n);if(n.startsWith("lock"))return List.of(1);return 1;
  }));
  ReflectionTestUtils.setField(svc,"lifecycle",new ProductLifecycleService(new ProductStatusAccess(null,null)){
   @Override public void refreshSupplyStates(Collection<Integer> ids,boolean restore){events.add("restore:"+restore);}
  });return svc;
 }
 @Test public void cancellationReleasesOnceRestoresAndLocksProductsFirst(){
  var order=new Orders();order.setOrdersStatus(0);var events=new ArrayList<String>();var svc=service(order,events);
  svc.updateStatusByLoad(1,4);
  assertEquals(List.of("lockProducts","lockSkus","releaseOutbound","restore:true","reopenProductsWithListedSkus","closeProductsWithoutListedSkus"),events);
  svc.updateStatusByLoad(1,4);assertEquals(6,events.size());
 }
 @Test public void shipmentNeverEnablesSoldOutRecovery(){
  var order=new Orders();order.setOrdersStatus(0);var events=new ArrayList<String>();service(order,events).updateStatusByLoad(1,1);
  assertEquals(List.of("lockProducts","lockSkus","shipStock","restore:false","closeProductsWithoutListedSkus"),events);
 }
}
