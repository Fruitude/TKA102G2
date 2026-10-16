package com.fruitude.product.model;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Observes committed inventory writes; only time boundaries use scheduled database reads. */
@Component
@ConditionalOnProperty(name="product.availability-scheduler.enabled",havingValue="true",matchIfMissing=true)
public class ProductAvailabilityScheduler {
    private final NamedParameterJdbcTemplate jdbc;
    private final SkuSupplyService supply;
    private final FrontCatalogService catalog;
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{
        var thread=new Thread(r,"product-inventory-events");thread.setDaemon(true);return thread;
    });
    private final Map<Integer,Set<Integer>> pending=new ConcurrentHashMap<>();
    private final Map<Integer,ScheduledFuture<?>> clocks=new ConcurrentHashMap<>();
    private final AtomicBoolean queued=new AtomicBoolean();
    private final AtomicBoolean stopped=new AtomicBoolean();
    private java.nio.file.Path journalPath;
    private boolean startupReconcile=true;
    private final Map<String,Delivery> journal=new ConcurrentHashMap<>();
    private static final class Delivery {
        final Changes changes;Set<Integer> targets;
        Delivery(Changes changes){this.changes=changes;}
    }
    private static final Object TRANSACTION_BATCH=new Object();
    public ProductAvailabilityScheduler(NamedParameterJdbcTemplate jdbc,SkuSupplyService supply,FrontCatalogService catalog) {
        this.jdbc=jdbc;this.supply=supply;this.catalog=catalog;
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ProductAvailabilityScheduler(NamedParameterJdbcTemplate jdbc,SkuSupplyService supply,FrontCatalogService catalog,
            org.springframework.core.env.Environment environment) {
        this(jdbc,supply,catalog);
        startupReconcile=environment.getProperty("product.inventory-events.startup-reconcile.enabled",Boolean.class,true);
        String key=System.getProperty("user.dir")+environment.getProperty("spring.datasource.url","");
        journalPath=java.nio.file.Path.of(environment.getProperty("product.inventory-events.journal-path",
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"fruitude-product-events-"+Integer.toHexString(key.hashCode())+".log").toString()));
    }
    static final class Changes {
        final Map<Integer,Boolean> skus=new TreeMap<>();
        final Map<Integer,Long> receipts=new TreeMap<>();
        final Set<Integer> cancellations=new TreeSet<>();
        final Set<Integer> products=new TreeSet<>();
        void sku(int id,boolean recovery) {skus.merge(id,recovery,Boolean::logicalOr);}
        void receipt(int id,long delta) {
            receipts.merge(id,delta,Long::sum);
            skus.put(id,receipts.get(id)>0 || cancellations.contains(id));
        }
        void cancellation(int id) {cancellations.add(id);sku(id,true);}
    }
    /** Register while the writer's transaction is active. Rollback never publishes. */
    void observe(Changes changes) {
        if(changes.skus.isEmpty() && changes.products.isEmpty())return;
        if(!TransactionSynchronizationManager.isSynchronizationActive()) {publish(changes);return;}
        var batch=(Changes)TransactionSynchronizationManager.getResource(TRANSACTION_BATCH);
        if(batch==null) {
            batch=new Changes();final var committed=batch;
            TransactionSynchronizationManager.bindResource(TRANSACTION_BATCH,batch);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                private final Map<Object,Changes> savepoints=new IdentityHashMap<>();
                @Override public void suspend(){TransactionSynchronizationManager.unbindResourceIfPossible(TRANSACTION_BATCH);}
                @Override public void resume(){TransactionSynchronizationManager.bindResource(TRANSACTION_BATCH,committed);}
                @Override public void savepoint(Object point){
                    var copy=new Changes();copy.skus.putAll(committed.skus);copy.products.addAll(committed.products);
                    copy.receipts.putAll(committed.receipts);copy.cancellations.addAll(committed.cancellations);savepoints.put(point,copy);
                }
                @Override public void savepointRollback(Object point){
                    var copy=savepoints.get(point);
                    if(copy!=null){
                        committed.skus.clear();committed.skus.putAll(copy.skus);committed.products.clear();committed.products.addAll(copy.products);
                        committed.receipts.clear();committed.receipts.putAll(copy.receipts);
                        committed.cancellations.clear();committed.cancellations.addAll(copy.cancellations);
                    }
                }
                @Override public void afterCommit(){publish(committed);}
                @Override public void afterCompletion(int status){TransactionSynchronizationManager.unbindResourceIfPossible(TRANSACTION_BATCH);}
            });
        }
        changes.skus.forEach(batch::sku);changes.cancellations.forEach(batch::cancellation);
        changes.receipts.forEach(batch::receipt);batch.products.addAll(changes.products);
    }
    private void publish(Changes changes) {
        // No database access here: failure after commit must not turn a successful order into an error.
        var copy=new Changes();copy.skus.putAll(changes.skus);copy.products.addAll(changes.products);
        String key=UUID.randomUUID().toString();journal.put(key,new Delivery(copy));persistJournal();
        dispatch(key);
    }
    private void dispatch(String key) {
        try {worker.execute(()->{
            var delivery=journal.get(key);if(delivery==null)return;
            var changes=delivery.changes;
            try {
                var recovery=new HashSet<Integer>();
                changes.skus.forEach((id,allow)->{if(allow)recovery.add(id);});
                var ids=new TreeSet<>(changes.products);
                if(!changes.skus.isEmpty()) {
                    for(var row:jdbc.queryForList("SELECT DISTINCT product_id FROM product_sku WHERE sku_id IN (:ids)",Map.of("ids",changes.skus.keySet())))
                        ids.add(((Number)row.get("product_id")).intValue());
                }
                delivery.targets=new HashSet<>(ids);
                if(ids.isEmpty()){journal.remove(key);persistJournal();}
                else ids.forEach(id->enqueue(id,recovery));
            }catch(RuntimeException error){
                log.error("商品事件轉換失敗，5 秒後重試",error);
                worker.schedule(()->dispatch(key),5,TimeUnit.SECONDS);
            }
        });} catch(RejectedExecutionException error){log.error("商品事件工作已關閉；下次啟動將補查狀態",error);}
    }
    private void enqueue(int id,Set<Integer> recovery) {
        pending.merge(id,Set.copyOf(recovery),(a,b)->{var combined=new HashSet<>(a);combined.addAll(b);return Set.copyOf(combined);});
        requestDrain(100);
    }
    private void requestDrain(long delay) {
        if(!stopped.get() && queued.compareAndSet(false,true))worker.schedule(this::drain,delay,TimeUnit.MILLISECONDS);
    }
    private void drain() {
        queued.set(false);
        for(var item:new TreeMap<>(pending).entrySet()) {
            try {
                supply.refreshChangedProducts(Set.of(item.getKey()),item.getValue());
                catalog.refreshRankedProductCards(Set.of(item.getKey()));
                scheduleBoundary(item.getKey());
                pending.remove(item.getKey(),item.getValue());
                boolean completed=journal.entrySet().removeIf(entry->{
                    var targets=entry.getValue().targets;
                    if(targets==null)return false;
                    targets.remove(item.getKey());return targets.isEmpty();
                });
                if(completed)persistJournal();
            }catch(RuntimeException error){log.warn("商品 {} 狀態同步失敗，保留事件並重試",item.getKey(),error);}
        }
        if(!pending.isEmpty())requestDrain(5000);
    }
    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initialize() {
        if(!startupReconcile)return;
        log.info("商品庫存使用提交後事件同步；未完成事件暫存位置：{}",journalPath);
        worker.execute(()->{
            try {
                try{restoreJournal();}catch(java.io.IOException error){log.error("商品事件暫存讀取失敗，仍執行啟動補查",error);}
                journal.keySet().forEach(this::dispatch);
                var ids=jdbc.queryForList("SELECT product_id FROM product",Map.of(),Integer.class);
                ids.forEach(id->enqueue(id,Set.of()));
            }catch(RuntimeException error){log.warn("商品啟動補查失敗，5 秒後重試",error);worker.schedule(this::initialize,5,TimeUnit.SECONDS);}
        });
    }
    private synchronized void persistJournal() {
        if(journalPath==null)return;
        try {
            var lines=new ArrayList<String>();
            journal.forEach((key,delivery)->{
                String skus=delivery.changes.skus.entrySet().stream().map(e->e.getKey()+":"+(e.getValue()?"1":"0")).collect(java.util.stream.Collectors.joining(","));
                String products=delivery.changes.products.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
                lines.add(key+"|"+skus+"|"+products);
            });
            java.nio.file.Files.createDirectories(journalPath.toAbsolutePath().getParent());
            var temporary=journalPath.resolveSibling(journalPath.getFileName()+".tmp");
            java.nio.file.Files.write(temporary,lines,java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.move(temporary,journalPath,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }catch(java.io.IOException error){log.error("商品事件暫存寫入失敗，事件仍保留在記憶體；請檢查 {}",journalPath,error);}
    }
    private synchronized void restoreJournal() throws java.io.IOException {
        if(journalPath==null || !java.nio.file.Files.exists(journalPath))return;
        for(var line:java.nio.file.Files.readAllLines(journalPath,java.nio.charset.StandardCharsets.UTF_8)) {
            String[] fields=line.split("\\|",-1);
            try {
                var changes=new Changes();
                if(!fields[1].isBlank())for(var token:fields[1].split(",")){var pair=token.split(":");changes.sku(Integer.parseInt(pair[0]),pair[1].equals("1"));}
                if(!fields[2].isBlank())for(var token:fields[2].split(","))changes.products.add(Integer.parseInt(token));
                journal.putIfAbsent(fields[0],new Delivery(changes));
            }catch(RuntimeException error){log.error("商品事件暫存格式錯誤，略過此筆",error);}
        }
    }
    private void scheduleBoundary(int id) {
        var old=clocks.remove(id);if(old!=null)old.cancel(false);
        var now=LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        String sql="""
            SELECT MIN(boundary) FROM (
              SELECT s.default_listing_time AS boundary FROM product_sku s JOIN product p ON p.product_id=s.product_id
                WHERE s.product_id=:id AND s.status=5 AND p.status<>2 AND s.default_listing_time IS NOT NULL
              UNION ALL
              SELECT a.promo_project_start AS boundary FROM promo_project a JOIN promotion pr ON pr.promo_project_id=a.promo_project_id
                JOIN product_sku s ON s.sku_id=pr.sku_id WHERE s.product_id=:id AND a.status=1
              UNION ALL
              SELECT a.promo_project_end FROM promo_project a JOIN promotion pr ON pr.promo_project_id=a.promo_project_id
                JOIN product_sku s ON s.sku_id=pr.sku_id WHERE s.product_id=:id AND a.status=1
              UNION ALL
              SELECT DATE_ADD(CASE WHEN po.inbound_status=3 THEN COALESCE(po.inbound_date,po.order_date) ELSE po.inbound_date END, INTERVAL 90 DAY)
                + INTERVAL 1 SECOND FROM purchaseorder_details d JOIN purchaseorder po ON po.id=d.po_id
                JOIN product_sku s ON s.sku_id=d.sku_id JOIN product p ON p.product_id=s.product_id
                WHERE s.product_id=:id AND po.vendor_id=p.vendor_id AND po.po_status IN(1,4) AND po.inbound_status IN(1,2,3)
            ) boundaries WHERE boundary>:now
            """;
        var timestamp=jdbc.queryForObject(sql,Map.of("id",id,"now",now),java.sql.Timestamp.class);
        if(timestamp!=null) {
            long delay=Math.max(1,Duration.between(now,timestamp.toLocalDateTime()).toMillis());
            clocks.put(id,worker.schedule(()->enqueue(id,Set.of()),delay,TimeUnit.MILLISECONDS));
        }
    }
    @jakarta.annotation.PreDestroy
    public void stop(){stopped.set(true);worker.shutdownNow();}

    /** Uses Spring's existing proxy API; other teams' repository/service sources stay unchanged. */
    @Component
    @ConditionalOnProperty(name="product.availability-scheduler.enabled",havingValue="true",matchIfMissing=true)
    public static class WriteObserver implements BeanPostProcessor {
        private final BeanFactory beans;
        private static final Set<String> STOCK_WRITES=Set.of("deductStock","shipStock","releaseOutbound","releaseStock","addInboundQty","receiveStock","adjustStock","restoreStock","updateQuantities");
        private static final Set<String> STOCK_BEANS=Set.of("skuStockRepository","shippingSkuStockRepository","poSkuStockRepository","stockSkuRepository");
        private static final Set<String> PRODUCT_BEANS=Set.of("productRepository","productSkuRepository","productImageRepository");
        private static final Set<String> TIMING_BEANS=Set.of("promoRepository","promotionRepository");
        public WriteObserver(BeanFactory beans){this.beans=beans;}
        @Override public Object postProcessAfterInitialization(Object bean,String name) {
            if(!STOCK_BEANS.contains(name) && !PRODUCT_BEANS.contains(name) && !TIMING_BEANS.contains(name)
                && !name.equals("receivingService") && !name.equals("poReviewService"))return bean;
            MethodInterceptor observer=invocation->{
                String method=invocation.getMethod().getName();Object[] args=invocation.getArguments();
                boolean stock=STOCK_BEANS.contains(name) && STOCK_WRITES.contains(method);
                boolean entity=(PRODUCT_BEANS.contains(name)||STOCK_BEANS.contains(name)||TIMING_BEANS.contains(name))
                    && (method.startsWith("save")||method.startsWith("delete"));
                boolean receiving=name.equals("receivingService") && method.equals("receive");
                boolean approving=name.equals("poReviewService") && method.equals("approve");
                if(!stock && !entity && !receiving && !approving)return invocation.proceed();
                Changes changes=new Changes();
                // Read IDs before deletion. Events only publish after the enclosing transaction succeeds.
                if(entity && method.startsWith("delete"))collectArguments(name,args,changes);
                Object result=invocation.proceed();
                if(stock && result instanceof Number count && count.intValue()>0) {
                    int id=((Number)args[0]).intValue();long quantity=((Number)args[1]).longValue();
                    changes.sku(id,false);
                    if(method.equals("receiveStock")||method.equals("adjustStock"))changes.receipt(id,quantity);
                    if((method.equals("releaseOutbound")||method.equals("releaseStock")) && quantity>0)changes.cancellation(id);
                }
                if(entity && method.startsWith("save"))collectArguments(name,new Object[]{result},changes);
                if(receiving||approving) {
                    Integer poId=receiving?((com.fruitude.po.model.PoVO)args[0]).getPoId():(Integer)args[0];
                    var jdbc=beans.getBean(NamedParameterJdbcTemplate.class);
                    for(var id:jdbc.queryForList("SELECT sku_id FROM purchaseorder_details WHERE po_id=:id",Map.of("id",poId),Integer.class))changes.sku(id,false);
                }
                beans.getBean(ProductAvailabilityScheduler.class).observe(changes);
                return result;
            };
            if(bean instanceof Advised proxy && !proxy.isFrozen()) {
                int position=0;
                if(name.equals("receivingService")||name.equals("poReviewService")) {
                    var advisors=proxy.getAdvisors();
                    for(int i=0;i<advisors.length;i++)if(advisors[i].getAdvice() instanceof TransactionInterceptor)position=i+1;
                }
                proxy.addAdvice(position,observer);return bean;
            }
            var factory=new ProxyFactory(bean);factory.setProxyTargetClass(!bean.getClass().isInterface());factory.addAdvice(observer);
            return factory.getProxy();
        }
        private void collectArguments(String bean,Object[] args,Changes changes) {
            for(var arg:args)collect(arg,changes);
            if(changes.skus.isEmpty() && changes.products.isEmpty()) {
                var jdbc=beans.getBean(NamedParameterJdbcTemplate.class);
                if(PRODUCT_BEANS.contains(bean)||STOCK_BEANS.contains(bean)) {
                    if(args.length>0 && args[0] instanceof Number id) {
                        if(bean.equals("productRepository"))changes.products.add(id.intValue());
                        else if(bean.equals("productImageRepository")) {
                            for(var sku:jdbc.queryForList("SELECT sku_id FROM product_image WHERE image_id=:id",Map.of("id",id),Integer.class))changes.sku(sku,false);
                        }else changes.sku(id.intValue(),false);
                    }else for(var id:jdbc.queryForList("SELECT product_id FROM product",Map.of(),Integer.class))changes.products.add(id);
                }
            }
            if(TIMING_BEANS.contains(bean)) {
                for(var id:beans.getBean(NamedParameterJdbcTemplate.class).queryForList("SELECT DISTINCT product_id FROM product_sku",Map.of(),Integer.class))changes.products.add(id);
            }
        }
        private static void collect(Object value,Changes changes) {
            if(value instanceof Product p && p.getProductId()!=null)changes.products.add(p.getProductId());
            else if(value instanceof ProductSku s && s.getSkuId()!=null)changes.sku(s.getSkuId(),false);
            else if(value instanceof ProductImage image && image.getProductSku()!=null)collect(image.getProductSku(),changes);
            else if(value instanceof Iterable<?> values)values.forEach(v->collect(v,changes));
        }
    }
}
