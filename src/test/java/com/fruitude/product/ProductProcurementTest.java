package com.fruitude.product;

import com.fruitude.product.model.*;
import com.fruitude.po.model.*;
import com.fruitude.podetail.model.*;
import com.fruitude.vendor.model.VendorVO;
import java.math.BigDecimal;
import java.util.*;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductProcurementTest {
 @SuppressWarnings("unchecked") static <T>T fake(Class<T> c,java.util.function.BiFunction<String,Object[],Object> f){
  return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(p,m,a)->f.apply(m.getName(),a));
 }
 ProductSku sku(){var s=new ProductSku();s.setSkuId(1);s.setStatus((byte)2);s.setStock(0);s.setInboundQty(10);s.setOutboundQty(5);s.setSafetyStock(10);s.setPurchaseAddOnQty(5);return s;}
 @Test public void yieldConvertsGoodBoxDeficitToOrderQuantityThenSubtractsPending(){
  var s=sku();assertTrue(SkuSupplyService.needsPurchase(s,new BigDecimal(".5")));
  assertEquals(Integer.valueOf(30),ProductSkuService.suggestedQuantity(s,new BigDecimal(".5"),0));
  assertEquals(Integer.valueOf(23),ProductSkuService.suggestedQuantity(s,new BigDecimal(".5"),7));
  assertEquals(Integer.valueOf(0),ProductSkuService.suggestedQuantity(s,new BigDecimal(".5"),100));
  assertNull(ProductSkuService.suggestedQuantity(s,BigDecimal.ZERO,0));
 }
 @Test public void lowerYieldFindsShortageThatRawIncomingWouldHideAndCapsSuggestions(){
  var s=sku();s.setOutboundQty(0);assertFalse(SkuSupplyService.needsPurchase(s,BigDecimal.ONE));
  assertTrue(SkuSupplyService.needsPurchase(s,new BigDecimal(".5")));
  s.setInboundQty(0);s.setSafetyStock(100000);assertEquals(Integer.valueOf(9999),ProductSkuService.suggestedQuantity(s,new BigDecimal(".5"),0));
  assertEquals(Integer.valueOf(100000),s.getShortageQty());s.setPurchaseYield(new BigDecimal(".5"));assertEquals(Integer.valueOf(200000),s.getShortageQty());
 }
 @Test public void currentPendingOrderIsExcludedWithoutChangingItsStoredDetails(){
  var s=sku();var p=new Product();var v=new VendorVO();v.setVendorId(7);p.setVendor(v);s.setProduct(p);
  var d=new PoDetailVO();d.setSkuId(s);d.setQuantity(7);var po=new PoVO();po.setPoId(9);po.setPoStatus((byte)0);po.setPoDetails(List.of(d));
  var service=new ProductSkuService(fake(ProductSkuRepository.class,(n,a)->List.of(s)),
   new SkuSupplyService(null){@Override public Map<Integer,BigDecimal> yields(Collection<Integer> ids){return Map.of(1,new BigDecimal(".5"));}},
   new PoService(){@Override public Map<Integer,Long> getPendingQuantityBySkuId(){return Map.of(1,12L);}},
   fake(PoRepository.class,(n,a)->Optional.of(po)),null);
  assertEquals(Integer.valueOf(18),service.getSuggestedQuantityBySkuId(7,null).get(1));
  assertEquals(Integer.valueOf(25),service.getSuggestedQuantityBySkuId(7,9).get(1));
  assertEquals(Integer.valueOf(7),d.getQuantity());
 }

 @Test public void newShortageDetailsReadCandidatesYieldAndPendingOnlyOnce(){
  var reads=new int[4];var first=sku();var covered=sku();covered.setSkuId(2);
  var product=new Product();var vendor=new VendorVO();vendor.setVendorId(7);product.setVendor(vendor);
  first.setProduct(product);covered.setProduct(product);
  var service=new ProductSkuService(fake(ProductSkuRepository.class,(name,args)->{reads[0]++;return List.of(first,covered);}),
   new SkuSupplyService(null){@Override public Map<Integer,BigDecimal> yields(Collection<Integer> ids){reads[1]++;return Map.of(1,new BigDecimal(".5"),2,new BigDecimal(".5"));}},
   new PoService(){@Override public Map<Integer,Long> getPendingQuantityBySkuId(){reads[2]++;return Map.of(2,100L);}},null,null);
  var details=service.getShortageDetailsByVendorId(7);
  assertEquals(List.of(first),details.stream().map(PoDetailVO::getSkuId).toList());
  assertArrayEquals(new int[]{1,1,1,0},reads);
 }
 @Test public void editedOrderReusesLoadedOrderAndShortageSnapshotWithoutChangingSavedDetails(){
  var reads=new int[4];var existing=sku();var additional=sku();additional.setSkuId(2);
  var product=new Product();var vendor=new VendorVO();vendor.setVendorId(7);product.setVendor(vendor);
  existing.setProduct(product);additional.setProduct(product);
  var detail=new PoDetailVO();detail.setSkuId(existing);detail.setQuantity(7);
  var po=new PoVO();po.setPoId(9);po.setVendor(vendor);po.setPoStatus((byte)0);po.setPoDetails(List.of(detail));
  var service=new ProductSkuService(fake(ProductSkuRepository.class,(name,args)->{reads[0]++;return List.of(existing,additional);}),
   new SkuSupplyService(null){@Override public Map<Integer,BigDecimal> yields(Collection<Integer> ids){reads[1]++;return Map.of(1,new BigDecimal(".5"),2,new BigDecimal(".5"));}},
   new PoService(){@Override public Map<Integer,Long> getPendingQuantityBySkuId(){reads[2]++;return Map.of(1,12L,2,4L);}},
   fake(PoRepository.class,(name,args)->{reads[3]++;return Optional.of(po);}),null);
  var form=service.getPoWithShortageDetails(9);
  assertArrayEquals(new int[]{1,1,1,1},reads);
  assertEquals(List.of(existing,additional),form.getPoDetails().stream().map(PoDetailVO::getSkuId).toList());
  assertEquals(Integer.valueOf(7),form.getPoDetails().get(0).getQuantity());
  assertEquals(1,po.getPoDetails().size());assertSame(detail,po.getPoDetails().get(0));
 }

}
