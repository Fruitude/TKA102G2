package com.fruitude.product.model;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrontCatalogFreshnessTest {
    private static class EventFixture implements AutoCloseable {
        final List<Set<Integer>> recoveries=new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger failNext=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.CountDownLatch synchronizedOnce=new java.util.concurrent.CountDownLatch(1);
        final org.springframework.beans.factory.support.DefaultListableBeanFactory beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        final ProductAvailabilityScheduler scheduler;
        final ProductAvailabilityScheduler.WriteObserver observer;
        EventFixture() {
            var jdbc=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource()) {
                @Override public List<Map<String,Object>> queryForList(String sql,Map<String,?> args){return List.of(Map.of("product_id",9));}
                @Override public <T>T queryForObject(String sql,Map<String,?> args,Class<T> type){return null;}
                @Override public <T>List<T> queryForList(String sql,Map<String,?> args,Class<T> type){return List.of(type.cast(sql.equals("SELECT product_id FROM product")?9:2));}
            };
            var supply=new SkuSupplyService(jdbc){
                @Override public void refreshChangedProducts(Collection<Integer> ids,Set<Integer> recovery){
                    if(failNext.getAndUpdate(n->Math.max(0,n-1))>0)throw new IllegalStateException("simulated transient failure");
                    assertEquals(Set.of(9),new HashSet<>(ids));recoveries.add(Set.copyOf(recovery));
                }
            };
            scheduler=new ProductAvailabilityScheduler(jdbc,supply,new FrontCatalogService(){
                @Override public void refreshRankedProductCards(Collection<Integer> ids){synchronizedOnce.countDown();}
            });
            beans.registerSingleton("scheduler",scheduler);beans.registerSingleton("jdbc",jdbc);
            observer=new ProductAvailabilityScheduler.WriteObserver(beans);
        }
        Object batch(){return org.springframework.transaction.support.TransactionSynchronizationManager.getResource(
            org.springframework.test.util.ReflectionTestUtils.getField(ProductAvailabilityScheduler.class,"TRANSACTION_BATCH"));}
        void begin(){org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();}
        void complete(boolean commit){
            if(commit)org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCommit();
            org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCompletion(commit?0:1);
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        void await() throws Exception {assertTrue(synchronizedOnce.await(3,java.util.concurrent.TimeUnit.SECONDS));}
        public void close(){scheduler.stop();if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())complete(false);}
    }
    private com.fruitude.orders.model.SkuStockRepository observedStock(EventFixture f) {
        var target=proxy(com.fruitude.orders.model.SkuStockRepository.class,(p,m,a)->m.getName().startsWith("lock")?List.of(2):1);
        var factory=new org.springframework.aop.framework.ProxyFactory(target);
        return (com.fruitude.orders.model.SkuStockRepository)f.observer.postProcessAfterInitialization(factory.getProxy(),"skuStockRepository");
    }
    @Test public void transactionDeduplicatesSkuAndKeepsCancellationReason() throws Exception {
        try(var f=new EventFixture()) {
            f.begin();var stock=observedStock(f);
            stock.deductStock(2,1,java.math.BigDecimal.ONE);stock.shipStock(2,1);stock.releaseOutbound(2,1);
            var batch=(ProductAvailabilityScheduler.Changes)f.batch();assertEquals(Map.of(2,true),batch.skus);
            assertEquals(1,org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().size());
            assertTrue(f.recoveries.isEmpty());f.complete(true);f.await();assertEquals(List.of(Set.of(2)),f.recoveries);
        }
    }
    @Test public void rolledBackStockChangeNeverPublishesAndStatusOnlyWriteIsNotAnInventoryEvent() {
        try(var f=new EventFixture()) {
            f.begin();var stock=observedStock(f);stock.shipStock(2,1);
            stock.closeProductsWithoutListedSkus(List.of(2));
            assertEquals(Map.of(2,false),((ProductAvailabilityScheduler.Changes)f.batch()).skus);
            f.complete(false);assertNull(f.batch());assertTrue(f.recoveries.isEmpty());
        }
    }
    @Test public void incomingAndShippingDoNotReviveSoldOutButPositiveReceiptDoes() {
        try(var f=new EventFixture()) {
            var target=proxy(com.fruitude.po.model.PoSkuStockRepository.class,(p,m,a)->1);
            var stock=(com.fruitude.po.model.PoSkuStockRepository)f.observer.postProcessAfterInitialization(new org.springframework.aop.framework.ProxyFactory(target).getProxy(),"poSkuStockRepository");
            f.begin();stock.addInboundQty(2,10);stock.receiveStock(2,0,10);stock.adjustStock(2,-2);
            assertEquals(Map.of(2,false),((ProductAvailabilityScheduler.Changes)f.batch()).skus);
            stock.adjustStock(2,3);assertEquals(Map.of(2,true),((ProductAvailabilityScheduler.Changes)f.batch()).skus);
            stock.adjustStock(2,-1);assertEquals(Map.of(2,false),((ProductAvailabilityScheduler.Changes)f.batch()).skus);f.complete(false);
        }
    }
    @Test public void failedConditionalSqlAndReadMethodsDoNotPublish() {
        try(var f=new EventFixture()) {
            var target=proxy(com.fruitude.orders.model.SkuStockRepository.class,(p,m,a)->m.getName().startsWith("lock")?List.of(2):0);
            var stock=(com.fruitude.orders.model.SkuStockRepository)f.observer.postProcessAfterInitialization(new org.springframework.aop.framework.ProxyFactory(target).getProxy(),"skuStockRepository");
            f.begin();stock.lockSkus(List.of(2));stock.deductStock(2,1,java.math.BigDecimal.ONE);
            assertNull(f.batch());f.complete(false);
        }
    }
    @Test public void savepointRollbackDiscardsOnlyNestedChanges() {
        try(var f=new EventFixture()) {
            f.begin();var stock=observedStock(f);stock.shipStock(2,1);
            var synchronization=org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().get(0);
            Object point=new Object();synchronization.savepoint(point);stock.releaseOutbound(2,1);stock.shipStock(3,1);
            synchronization.savepointRollback(point);
            assertEquals(Map.of(2,false),((ProductAvailabilityScheduler.Changes)f.batch()).skus);f.complete(false);
        }
    }
    @Test public void productCascadeSavePublishesParentWithoutPollingTheCatalog() {
        try(var f=new EventFixture()) {
            var target=proxy(ProductRepository.class,(p,m,a)->a[0]);
            var products=(ProductRepository)f.observer.postProcessAfterInitialization(new org.springframework.aop.framework.ProxyFactory(target).getProxy(),"productRepository");
            var p=new Product();p.setProductId(9);f.begin();products.saveAndFlush(p);
            assertEquals(Set.of(9),((ProductAvailabilityScheduler.Changes)f.batch()).products);f.complete(false);
        }
    }
    @Test public void committedEventRetriesTransientFailureWithoutFailingTheWriter() throws Exception {
        try(var f=new EventFixture()) {
            f.failNext.set(1);f.begin();observedStock(f).releaseOutbound(2,1);f.complete(true);
            assertTrue(f.synchronizedOnce.await(8,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(List.of(Set.of(2)),f.recoveries);
        }
    }
    @Test public void restartRestoresPendingReceiptRecoveryFromJournal() throws Exception {
        var path=java.nio.file.Files.createTempFile("product-event-test-",".log");
        try(var f=new EventFixture()) {
            java.nio.file.Files.writeString(path,"restart-test|2:1|\n");
            org.springframework.test.util.ReflectionTestUtils.setField(f.scheduler,"journalPath",path);
            f.scheduler.initialize();f.await();assertEquals(List.of(Set.of(2)),f.recoveries);
        }finally{java.nio.file.Files.deleteIfExists(path);java.nio.file.Files.deleteIfExists(path.resolveSibling(path.getFileName()+".tmp"));}
    }
    @SuppressWarnings("unchecked") private <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
    @Test public void nextCatalogRequestReflectsChangedPriceWithoutCacheInvalidation() {
        int[] price={100};
        var row=(com.fruitude.product.model.ProductRepository.FrontCatalogRow)Proxy.newProxyInstance(com.fruitude.product.model.ProductRepository.FrontCatalogRow.class.getClassLoader(),new Class<?>[]{com.fruitude.product.model.ProductRepository.FrontCatalogRow.class},(p,m,a)->switch(m.getName()) {
            case "getProductId","getSkuId","getSkuStatus" -> 1;
            case "getName","getSkuName" -> "水果";
            case "getPrice" -> price[0]; default -> null;
            case "getStock" -> 10;
        });
        var repo=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(p,m,a)->m.getName().equals("findFrontRows")?List.of(row):List.of());
        var service=new FrontCatalogService(repo);
        assertEquals(Integer.valueOf(100),service.getProducts().get(0).price());
        price[0]=200;
        assertEquals(Integer.valueOf(200),service.getProducts().get(0).price());
    }
    @Test public void inventoryEventsRefreshDailyCardsWithoutRequeryingRankings() {
        int[] price={100},stock={10},rankingQueries={0};
        var row=(ProductRepository.FrontCatalogRow)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.FrontCatalogRow.class},(p,m,a)->switch(m.getName()) {
            case "getProductId","getSkuId","getSkuStatus" -> 1;
            case "getName","getSkuName" -> "水果";
            case "getPrice" -> price[0];case "getStock" -> stock[0];default -> null;
        });
        var repo=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(p,m,a)->switch(m.getName()) {
            case "findBestSellerProductIds","findTopRatedProductIds" -> {rankingQueries[0]++;yield List.of(1);}
            case "findFrontRows" -> List.of(row);default -> List.of();
        });
        var service=new FrontCatalogService(repo);var initial=service.getBestSellers();
        price[0]=200;service.refreshRankedProductCards(List.of(1));
        assertEquals(Integer.valueOf(200),service.getBestSellers().get(0).price());
        assertEquals(Integer.valueOf(100),initial.get(0).price());assertEquals(2,rankingQueries[0]);
        stock[0]=0;service.refreshRankedProductCards(List.of(1));
        assertTrue(service.getBestSellers().isEmpty());assertTrue(service.getTopRatedProducts().isEmpty());
        stock[0]=3;service.refreshRankedProductCards(List.of(1));
        assertEquals(1,service.getBestSellers().size());assertEquals(2,rankingQueries[0]);
    }
}
