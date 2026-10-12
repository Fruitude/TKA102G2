package com.fruitude.product;

import com.fruitude.product.model.*;
import com.fruitude.vendor.model.VendorRepository;
import com.fruitude.po.model.*;
import com.fruitude.podetail.model.PoDetailRepository;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import static org.junit.Assert.*;

/** Exercises real Spring injection/proxies after merging independently wired services. No database is used. */
public class ProductConsolidationTest {
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean RecordingTransactions transactionManager() { return new RecordingTransactions(); }
    }
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        final ArrayList<Boolean> reads=new ArrayList<>();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx,TransactionDefinition definition) { reads.add(definition.isReadOnly()); }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
    @SuppressWarnings("unchecked") private static <T> T unused(Class<T> type) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)-> {
            if(method.getName().equals("toString"))return type.getSimpleName();
            if(method.getName().equals("hashCode"))return System.identityHashCode(object);
            if(method.getName().equals("equals"))return object==args[0];
            throw new AssertionError("Unexpected database access: "+method.getName());
        });
    }
    private AnnotationConfigApplicationContext context() {
        var context=new AnnotationConfigApplicationContext();var beans=context.getBeanFactory();
        beans.registerSingleton("products",unused(ProductRepository.class));
        beans.registerSingleton("skus",unused(ProductSkuRepository.class));
        beans.registerSingleton("categories",unused(ProductCategoryRepository.class));
        beans.registerSingleton("images",unused(ProductImageRepository.class));
        beans.registerSingleton("vendors",unused(VendorRepository.class));
        beans.registerSingleton("pos",unused(PoRepository.class));
        beans.registerSingleton("poDetails",unused(PoDetailRepository.class));
        beans.registerSingleton("poService",new PoService());
        beans.registerSingleton("jdbc",new NamedParameterJdbcTemplate(unused(DataSource.class)));
        beans.registerSingleton("entityManager",unused(EntityManager.class));
        beans.registerSingleton("statusAccess",new ProductStatusAccess(null,null));
        context.register(Transactions.class,ProductService.class,FrontCatalogService.class,ProductSkuService.class,ProductCategoryService.class,
            ProductImageService.class,ProductLifecycleService.class,SkuSupplyService.class,
            ProductInventoryLockService.class,ProductPageStatusService.class);
        context.refresh();return context;
    }
    @Test public void consolidatedServicesWireWithoutCircularDependenciesAndKeepReadWriteBoundaries() {
        try(var context=context()) {
            var product=context.getBean(ProductService.class);var sku=context.getBean(ProductSkuService.class);
            assertTrue(AopUtils.isAopProxy(product));assertTrue(AopUtils.isAopProxy(sku));
            var transactions=context.getBean(RecordingTransactions.class);
            try {context.getBean(FrontCatalogService.class).read(-1,0,3);fail();}catch(IllegalArgumentException expected) { }
            assertEquals(Boolean.TRUE,transactions.reads.get(0));
            try {product.save(null);fail();}catch(IllegalArgumentException expected) { }
            assertEquals(Boolean.FALSE,transactions.reads.get(1));
            assertNotNull(context.getBean(ProductPageStatusService.class));
        }
    }
    @Test public void lockingStillRequiresAnExistingTransaction() {
        try(var context=context()) {
            try {context.getBean(ProductInventoryLockService.class).lockProduct(1);fail();}
            catch(IllegalTransactionStateException expected) { }
        }
    }
}
