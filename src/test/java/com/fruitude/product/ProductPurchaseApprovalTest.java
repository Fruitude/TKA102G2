package com.fruitude.product;

import com.fruitude.product.model.*;
import com.fruitude.po.model.*;
import com.fruitude.podetail.model.*;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductPurchaseApprovalTest {
 @SuppressWarnings("unchecked") static <T>T fake(Class<T> c,java.util.function.BiFunction<String,Object[],Object> f){
  return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(p,m,a)->f.apply(m.getName(),a));
 }
 @Test public void approvalDelegatesWithoutOuterLocksOrRevivingSoldOut(){
  var p=new Product();p.setProductId(2);p.setStatus((byte)0);p.setAutoRestockEnabled(true);
  var s=new ProductSku();s.setSkuId(3);s.setProduct(p);s.setStatus((byte)4);s.setStock(0);s.setSafetyStock(10);p.getProductSkus().add(s);
  var line=new PoDetailVO();line.setSkuId(s);var po=new PoVO();po.setPoId(1);po.setPoDetails(List.of(line));
  var pos=fake(PoRepository.class,(n,a)->Optional.of(po));
  List<String> events=new ArrayList<>();
  var em=fake(EntityManager.class,(n,a)->{events.add(n);return null;});
  var locks=new ProductInventoryLockService(null,em){
   @Override public Optional<Product> lockProduct(Integer id){events.add("product-lock");return Optional.of(p);}
  };
  var review=new PoReviewService(){@Override public void approve(Integer id,String content){assertEquals("review-token",content);events.add("team-approve-clears-context");s.setInboundQty(100);}};
  var lifecycle=new ProductLifecycleService(new ProductStatusAccess(null,null)){
   @Override public void afterReceipt(Product product,Set<Integer> ids){fail("Approval is not an actual receipt");}
  };
  new ProductPurchaseFlowService(pos,null,review,locks,lifecycle,em).approve(1,"review-token");
  assertEquals(List.of("team-approve-clears-context"),events);
  assertEquals(Byte.valueOf((byte)4),s.getStatus());assertEquals(Byte.valueOf((byte)0),p.getStatus());assertEquals(Integer.valueOf(100),s.getInboundQty());
 }
}
