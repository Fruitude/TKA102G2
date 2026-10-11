package com.fruitude.product;

import com.fruitude.product.model.*;
import jakarta.persistence.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductInventoryLockTest {
 @SuppressWarnings("unchecked") static <T>T fake(Class<T> c,java.util.function.BiFunction<String,Object[],Object> f){
  return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(p,m,a)->f.apply(m.getName(),a));
 }
 @Test public void cachedQuantitiesAreReloadedAfterParentAndSkuLocks(){
  var p=new Product();p.setProductId(3);var sku=new ProductSku();sku.setSkuId(9);sku.setStock(1);sku.setOutboundQty(0);p.getProductSkus().add(sku);
  List<String> events=new ArrayList<>();
  final Query[] q=new Query[1];q[0]=fake(Query.class,(n,a)->switch(n){
   case "setParameter" -> {assertEquals(3,a[1]);yield q[0];}
   case "getResultList" -> {events.add("sku-lock");yield List.of(9);}
   default -> throw new AssertionError(n);
  });
  var em=fake(EntityManager.class,(n,a)->switch(n){
   case "createNativeQuery" -> {assertTrue(a[0].toString().endsWith("ORDER BY sku_id FOR UPDATE"));yield q[0];}
   case "refresh" -> {events.add(a[0]==p?"parent-refresh":"sku-refresh");if(a[0]==sku){sku.setStock(40);sku.setOutboundQty(12);}yield null;}
   default -> throw new AssertionError(n);
  });
  var repo=fake(ProductRepository.class,(n,a)->{assertEquals("lockForStatus",n);events.add("parent-lock");return Optional.of(p);});
  var locked=new ProductInventoryLockService(repo,em).lockProduct(3).orElseThrow();
  assertEquals(List.of("parent-lock","sku-lock","parent-refresh","sku-refresh"),events);
  assertEquals(Integer.valueOf(40),locked.getProductSkus().get(0).getStock());assertEquals(Integer.valueOf(12),sku.getOutboundQty());
 }
 @Test public void missingProductDoesNotAttemptChildLocks(){
  var repo=fake(ProductRepository.class,(n,a)->Optional.empty());
  var em=fake(EntityManager.class,(n,a)->{throw new AssertionError("No child query on missing product");});
  assertTrue(new ProductInventoryLockService(repo,em).lockProduct(3).isEmpty());
 }
}
