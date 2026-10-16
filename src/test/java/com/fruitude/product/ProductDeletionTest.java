package com.fruitude.product;

import com.fruitude.product.model.*;
import com.fruitude.product.controller.ProductController;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

public class ProductDeletionTest {
    static class Transactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        @Override protected Object doGetTransaction(){return new Object();}
        @Override protected void doBegin(Object tx,TransactionDefinition definition){assertFalse(definition.isReadOnly());}
        @Override protected void doCommit(DefaultTransactionStatus status){commits++;}
        @Override protected void doRollback(DefaultTransactionStatus status){rollbacks++;}
    }
    private ProductService service(Transactions tx, List<String> calls, boolean referenced) {
        var product=new Product();product.setProductId(89);
        var repo=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class[]{ProductRepository.class},(o,m,a)->{
            assertTrue("Deletion must run inside a transaction",TransactionSynchronizationManager.isActualTransactionActive());
            calls.add(m.getName());
            return switch(m.getName()) {
                case "findById" -> {assertEquals(89,a[0]);yield Optional.of(product);}
                case "delete" -> {assertSame(product,a[0]);yield null;}
                case "flush" -> {if(referenced)throw new org.springframework.dao.DataIntegrityViolationException("FK");yield null;}
                default -> throw new AssertionError("Unexpected query: "+m.getName());
            };
        });
        var service=new ProductService();ReflectionTestUtils.setField(service,"repository",repo);
        var proxy=new ProxyFactory(service);proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        return (ProductService)proxy.getProxy();
    }
    @Test public void usesActualIdAndDeletesAndFlushesInOneTransaction() {
        var tx=new Transactions();var calls=new ArrayList<String>();service(tx,calls,false).deleteProduct(89);
        assertEquals(List.of("findById","delete","flush"),calls);assertEquals(1,tx.commits);assertEquals(0,tx.rollbacks);
    }
    @Test public void foreignKeyFailureRollsBackAndReturnsOverviewWithoutSuccess() {
        var tx=new Transactions();var calls=new ArrayList<String>();var model=new RedirectAttributesModelMap();
        assertEquals("redirect:/product/listAllProduct",new ProductController(service(tx,calls,true)).delete(89,"",model));
        assertEquals(0,tx.commits);assertEquals(1,tx.rollbacks);
        assertTrue(model.getFlashAttributes().get("errorMessage").toString().contains("下架"));assertFalse(model.getFlashAttributes().containsKey("success"));
    }
    @Test public void unexpectedFailureReturnsOverviewAndNoFalseSuccess() {
        var model=new RedirectAttributesModelMap();var service=new ProductService(){@Override public void deleteProduct(Integer id){throw new IllegalStateException("Unexpected persistence error");}};
        assertEquals("redirect:/product/listAllProduct",new ProductController(service).delete(89,"",model));
        assertTrue(model.getFlashAttributes().containsKey("errorMessage"));assertFalse(model.getFlashAttributes().containsKey("success"));
    }
    @Test public void alreadyDeletedProductShowsNotFoundInsteadOfSuccess() {
        var model=new RedirectAttributesModelMap();var service=new ProductService(){@Override public void deleteProduct(Integer id){throw new IllegalArgumentException("商品不存在，可能已被刪除");}};
        new ProductController(service).delete(88,"",model);
        assertTrue(model.getFlashAttributes().get("errorMessage").toString().contains("不存在"));assertFalse(model.getFlashAttributes().containsKey("success"));
    }
    @Test public void successfulDeletionShowsSuccess() {
        var model=new RedirectAttributesModelMap();var service=new ProductService(){@Override public void deleteProduct(Integer id){assertEquals(Integer.valueOf(89),id);}};
        new ProductController(service).delete(89,"",model);assertEquals("刪除成功",model.getFlashAttributes().get("success"));
    }
    @Test public void deletionPreservesPageAndFiltersOnSuccessAndFailure() {
        for (boolean failure : List.of(false,true)) {
            var service=new ProductService(){@Override public void deleteProduct(Integer id){if(failure)throw new IllegalStateException("test");}};
            var model=new RedirectAttributesModelMap();
            String redirect=new ProductController(service).delete(89,"page=9&size=10&keyword=%E8%8A%92%E6%9E%9C+%E5%8F%B0%E5%8D%97&origin=%E5%8F%B0%E5%8D%97&sortBy=rating&sortDirection=desc",model);
            assertTrue(redirect.contains("page=9"));assertTrue(redirect.contains("size=10"));assertTrue(redirect.contains("sortDirection=desc"));
            assertTrue(redirect.contains("keyword=%E8%8A%92%E6%9E%9C%20%E5%8F%B0%E5%8D%97"));
            assertFalse(redirect.contains("%25E8"));
        }
    }
    @Test public void returnNavigationCannotChooseAnotherDestination() {
        var service=new ProductService(){@Override public void deleteProduct(Integer id){}};
        var model=new RedirectAttributesModelMap();
        assertEquals("redirect:/product/listAllProduct?page=9",new ProductController(service).delete(89,"returnTo=https://example.com&unknown=1&page=9",model));
        assertEquals("redirect:/product/listAllProduct",new ProductController(service).delete(89,"page=%ZZ",model));
    }
    @Test public void successFlashMessageSurvivesRedirectBackToFilteredPageNine() throws Exception {
        var service=new ProductService(){@Override public void deleteProduct(Integer id){}};
        var controller=new ProductController(service) {
            @Override protected List<ProductCategory> referenceProductCategoryListData(jakarta.servlet.http.HttpServletRequest request){return List.of();}
            @Override protected List<com.fruitude.vendor.model.VendorVO> referenceVendorListData(jakarta.servlet.http.HttpServletRequest request){return List.of();}
        };
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/product/delete")
            .param("productId","89").param("returnQuery","page=9&keyword=%E8%8A%92%E6%9E%9C+%E5%8F%B0%E5%8D%97"))
            .andReturn();
        assertEquals(302,result.getResponse().getStatus());
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/product/listAllProduct");
        request.setSession((org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false));
        request.addParameter("page","9");request.addParameter("keyword","芒果 台南");
        request.setQueryString(java.net.URI.create(result.getResponse().getRedirectedUrl()).getRawQuery());
        var flash=new org.springframework.web.servlet.support.SessionFlashMapManager().retrieveAndUpdate(request,new org.springframework.mock.web.MockHttpServletResponse());
        assertNotNull("Result message must reach the original filtered overview",flash);
        assertEquals("刪除成功",flash.get("success"));
    }
}
