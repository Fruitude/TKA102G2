package com.fruitude.po;

import com.fruitude.po.model.*;
import com.fruitude.podetail.model.*;
import com.fruitude.product.model.*;
import com.fruitude.employee.model.Employee;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;

/** Receiving owns its transaction; the product facade must not add locks or mutate stale entities. */
public class ReceivingSupplyTest {
 @SuppressWarnings("unchecked") static <T>T fake(Class<T> c,java.util.function.BiFunction<String,Object[],Object> f){
  return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(p,m,a)->f.apply(m.getName(),a));
 }
 static class Fixture {
  final Product product=new Product(); final ProductSku sku=new ProductSku();
  final PoVO db=new PoVO(), form=new PoVO(); final PoDetailVO line=new PoDetailVO(), input=new PoDetailVO();
  final List<String> events=new ArrayList<>(); final int[] quantities={0,20,12};
  final ReceivingService receiving=new ReceivingService(); final ProductPurchaseFlowService flow;
  int restores=0;
  Fixture() {
   product.setProductId(1);product.setStatus((byte)0);product.setAutoRestockEnabled(true);
   sku.setSkuId(2);sku.setProduct(product);sku.setStatus((byte)4);sku.setStock(0);sku.setInboundQty(20);sku.setOutboundQty(0);sku.setSafetyStock(10);product.getProductSkus().add(sku);
   line.setPoDetailId(3);line.setSkuId(sku);line.setQuantity(20);line.setUnitPrice(10);line.setArrivedPcs(0);line.setDefectPcs(0);line.setInboundPcs(0);
   db.setPoId(4);db.setPoStatus((byte)1);db.setInboundStatus((byte)0);db.setPoDetails(List.of(line));
   input.setPoDetailId(3);input.setArrivedPcs(18);input.setDefectPcs(2);
   form.setPoId(4);form.setInboundStatus((byte)1);form.setPoDetails(List.of(input));
   var pos=fake(PoRepository.class,(n,a)->switch(n){
    case "lockForReceiving" -> {events.add("po-lock");yield Optional.of(db);}
    case "findById" -> Optional.of(db);
    case "lockReceivablePo" -> {events.add("core-po-lock");yield db.getPoStatus()==1?List.of(4):List.of();}
    case "lockClosedPo" -> {events.add("core-po-lock");yield db.getPoStatus()==4?List.of(4):List.of();}
    default -> throw new AssertionError(n);
   });
   var em=fake(EntityManager.class,(n,a)->{
    if(n.equals("flush")){events.add("flush");return null;}
    if(n.equals("refresh")){events.add(a[0] instanceof ProductSku?"sku-refresh":"po-refresh");if(a[0]==sku){sku.setStock(quantities[0]);sku.setInboundQty(quantities[1]);sku.setOutboundQty(quantities[2]);}return null;}
    throw new AssertionError(n);
   });
   ReflectionTestUtils.setField(receiving,"poRepository",pos);
   ReflectionTestUtils.setField(receiving,"entityManager",em);
   ReflectionTestUtils.setField(receiving,"poSkuStockRepository",fake(PoSkuStockRepository.class,(n,a)->{
    if(n.equals("receiveStock")){events.add("receive-stock");quantities[0]+=(Integer)a[1];quantities[1]-=(Integer)a[2];return 1;}
    if(n.equals("adjustStock")){events.add("adjust-stock");quantities[0]+=(Integer)a[1];return quantities[0]>=0?1:0;}
    throw new AssertionError(n);
   }));
   var locks=new ProductInventoryLockService(null,em){
    @Override public Optional<Product> lockProduct(Integer id){events.add("product-sku-lock");reloadSkus(product);return Optional.of(product);}
   };
   var lifecycle=new ProductLifecycleService(new ProductStatusAccess(null,null)){
    @Override public void afterReceipt(Product p,Set<Integer> ids){restores++;events.add("restore");assertEquals(Set.of(2),ids);super.afterReceipt(p,ids);}
   };
   flow=new ProductPurchaseFlowService(pos,receiving,null,locks,lifecycle,em);
  }
  boolean receive(){return flow.receive(form,new Employee(),receiving.getReceivedContent(db));}
 }
 @Test public void receiptAddsOnlyGoodStockWithoutOuterLocksAndRejectsDuplicateSubmission(){
  var f=new Fixture();String original=f.receiving.getReceivedContent(f.db);
  assertFalse(f.flow.receive(f.form,new Employee(),original));
  assertArrayEquals(new int[]{16,0,12},f.quantities);
  assertEquals(Byte.valueOf((byte)4),f.sku.getStatus());assertEquals(Byte.valueOf((byte)0),f.product.getStatus());assertEquals(Byte.valueOf((byte)4),f.db.getPoStatus());
  assertEquals(Integer.valueOf(160),f.db.getInboundAmount());assertEquals(0,f.restores);
  assertEquals(1,Collections.frequency(f.events,"core-po-lock"));assertFalse(f.events.contains("po-lock"));
  assertFalse(f.events.contains("product-sku-lock"));assertFalse(f.events.contains("flush"));assertFalse(f.events.contains("restore"));
  try{f.flow.receive(f.form,new Employee(),original);fail("Stale content must be rejected");}catch(IllegalArgumentException expected){}
  assertEquals(16,f.quantities[0]);assertEquals(0,f.restores);
 }
 @Test public void unchangedOrReducedReceiptDoesNotReviveSoldOut(){
  var f=new Fixture();f.receive();f.sku.setStatus((byte)4);f.product.setStatus((byte)0);f.restores=0;
  assertTrue(f.receive());assertEquals(0,f.restores);assertEquals(Byte.valueOf((byte)4),f.sku.getStatus());
  f.input.setArrivedPcs(16);assertTrue(f.receive());assertEquals(0,f.restores);assertEquals(14,f.quantities[0]);
 }
 @Test public void increasedReceiptLeavesProductSynchronizationToCommittedEvents(){
  var f=new Fixture();f.receive();f.sku.setStatus((byte)4);f.product.setStatus((byte)0);f.restores=0;
  f.input.setArrivedPcs(20);assertTrue(f.receive());assertEquals(0,f.restores);assertEquals(18,f.quantities[0]);assertEquals(Byte.valueOf((byte)4),f.sku.getStatus());
  var manual=new Fixture();manual.product.setAutoRestockEnabled(false);manual.receive();
  assertEquals(Byte.valueOf((byte)4),manual.sku.getStatus());assertEquals(Byte.valueOf((byte)0),manual.product.getStatus());
 }
 @Test public void failedAndCancelledReceiptsReleaseIncomingWithoutRecovery(){
  for(byte status:new byte[]{2,3}){
   var f=new Fixture();f.form.setInboundStatus(status);f.receive();
   assertEquals(0,f.quantities[0]);assertEquals(0,f.quantities[1]);assertEquals(0,f.restores);
   assertEquals(Byte.valueOf((byte)4),f.sku.getStatus());
  }
 }
}
