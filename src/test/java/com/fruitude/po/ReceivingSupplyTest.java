package com.fruitude.po;
import com.fruitude.po.model.*;
import com.fruitude.podetail.model.*;
import com.fruitude.product.model.*;
import com.fruitude.orders.model.SkuStockRepository;
import com.fruitude.employee.model.Employee;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
public class ReceivingSupplyTest {
 @SuppressWarnings("unchecked") static <T>T fake(Class<T> c,java.util.function.BiFunction<String,Object[],Object> f){
  return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(p,m,a)->f.apply(m.getName(),a));
 }
 @Test public void receiptReloadsReservedQuantityAddsGoodStockAndOnlyRestoresOnce(){
  Product product=new Product();product.setProductId(1);product.setStatus((byte)0);
  ProductSku sku=new ProductSku();sku.setSkuId(2);sku.setProduct(product);sku.setStatus((byte)4);sku.setStock(0);sku.setInboundQty(20);sku.setOutboundQty(0);sku.setSafetyStock(10);product.getProductSkus().add(sku);
  PoDetailVO dbLine=new PoDetailVO();dbLine.setPoDetailId(3);dbLine.setSkuId(sku);dbLine.setQuantity(20);dbLine.setUnitPrice(10);
  PoVO db=new PoVO();db.setPoId(4);db.setPoStatus((byte)1);db.setInboundStatus((byte)0);db.setPoDetails(List.of(dbLine));
  PoDetailVO formLine=new PoDetailVO();formLine.setPoDetailId(3);formLine.setArrivedPcs(18);formLine.setDefectPcs(2);
  PoVO form=new PoVO();form.setPoId(4);form.setInboundStatus((byte)1);form.setPoDetails(List.of(formLine));
  List<String> events=new ArrayList<>();ReceivingService service=new ReceivingService();
  ReflectionTestUtils.setField(service,"poRepository",fake(PoRepository.class,(n,a)->{if(n.equals("lockForReceiving"))return Optional.of(db);if(n.equals("flush")){events.add("flush");return null;}throw new AssertionError(n);}));
  ReflectionTestUtils.setField(service,"products",fake(ProductRepository.class,(n,a)->{if(n.equals("lockForStatus")){events.add("product-lock");return Optional.of(product);}if(n.equals("saveAndFlush"))return a[0];throw new AssertionError(n);}));
  ReflectionTestUtils.setField(service,"stockLocks",fake(SkuStockRepository.class,(n,a)->{events.add("sku-lock");return List.of(2);}));
  ReflectionTestUtils.setField(service,"entityManager",fake(EntityManager.class,(n,a)->{assertEquals("refresh",n);events.add("refresh");sku.setOutboundQty(12);return null;}));
  ReflectionTestUtils.setField(service,"lifecycle",new ProductLifecycleService(new ProductStatusAccess(null,null)){
   @Override public void afterReceipt(Product p,Set<Integer> ids){events.add("restore");assertEquals(Set.of(2),ids);assertEquals(Integer.valueOf(16),sku.getStock());assertEquals(Integer.valueOf(0),sku.getInboundQty());assertEquals(Integer.valueOf(12),sku.getOutboundQty());super.afterReceipt(p,ids);}
  });
  service.receive(form,new Employee());
  assertEquals(List.of("product-lock","sku-lock","refresh","flush","restore"),events);
  assertEquals(Byte.valueOf((byte)3),sku.getStatus());assertEquals(Byte.valueOf((byte)1),product.getStatus());
  assertEquals(Integer.valueOf(160),db.getInboundAmount());
  try{service.receive(form,new Employee());fail("A receipt cannot add stock twice");}catch(IllegalArgumentException expected){}
  assertEquals(Integer.valueOf(16),sku.getStock());
 }
}
