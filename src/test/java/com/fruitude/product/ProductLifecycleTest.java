package com.fruitude.product;

import com.fruitude.product.model.*;
import com.fruitude.employee.model.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.junit.After;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;

public class ProductLifecycleTest {
    private ProductStatusAccess access(boolean admin) {
        return new ProductStatusAccess(null,null) { @Override public boolean canRestoreDiscontinued() { return admin; } };
    }
    private Product product(int state, int... states) {
        var p = new Product(); p.setProductId(10); p.setStatus((byte)state);
        for (int value : states) { var s = new ProductSku(); s.setSkuId(p.getProductSkus().size()+1); s.setStatus((byte)value); s.setStock(20); s.setOutboundQty(0); s.setProduct(p); p.getProductSkus().add(s); }
        return p;
    }
    @After public void clearSession() { RequestContextHolder.resetRequestAttributes(); }

    @Test public void takingProductOfflineClosesAllThreeSaleStatesButPreservesRetirement() {
        var p = product(1,1,2,3,4); new ProductLifecycleService(access(false)).changeProduct(p,(byte)0,false);
        assertEquals(Byte.valueOf((byte)0),p.getStatus());
        assertEquals(List.of((byte)0,(byte)0,(byte)0,(byte)4),p.getProductSkus().stream().map(ProductSku::getStatus).toList());
    }
    @Test public void listingRequiresConfirmationOnlyWithoutAllThreeSaleStates() {
        var lifecycle = new ProductLifecycleService(access(false));
        for (int state : new int[]{1,2,3}) { var p=product(0,state,4); lifecycle.changeProduct(p,(byte)1,false); assertEquals(Byte.valueOf((byte)1),p.getStatus()); assertEquals(Byte.valueOf((byte)state),p.getProductSkus().get(0).getStatus()); }
        var p=product(0,0,4);
        try { lifecycle.changeProduct(p,(byte)1,false); fail(); } catch (ProductStatusConfirmationException expected) {}
        assertEquals(Byte.valueOf((byte)0),p.getStatus());
        lifecycle.changeProduct(p,(byte)1,true);
        assertEquals(List.of((byte)1,(byte)4),p.getProductSkus().stream().map(ProductSku::getStatus).toList());
    }
    @Test public void productWithNoRestorableSkuCannotBeListedEvenAfterConfirmation() {
        var lifecycle=new ProductLifecycleService(access(true));
        for (var p : List.of(product(0),product(0,4))) {
            try { lifecycle.changeProduct(p,(byte)1,true); fail(); } catch (IllegalArgumentException expected) {}
            assertEquals(Byte.valueOf((byte)0),p.getStatus());
        }
    }
    @Test public void onlyAdminCanRestoreButEveryoneCanSetRetirement() {
        var normal=new ProductLifecycleService(access(false)); var admin=new ProductLifecycleService(access(true));
        var p=product(1,1,4); normal.changeProduct(p,(byte)2,false); normal.changeProduct(p,(byte)2,false);
        normal.validateSkuChange((byte)1,(byte)4); normal.validateSkuChange((byte)4,(byte)4);
        try { normal.changeProduct(p,(byte)0,false); fail(); } catch(ProductStatusAccessException expected) {}
        try { normal.validateSkuChange((byte)4,(byte)1); fail(); } catch(ProductStatusAccessException expected) {}
        admin.validateSkuChange((byte)4,(byte)1); admin.changeProduct(p,(byte)1,false);
        assertEquals(Byte.valueOf((byte)1),p.getStatus());
    }
    @Test public void automaticChangesNeverRestoreRetiredProductsOrSkus() {
        var lifecycle=new ProductLifecycleService(access(false)); var p=product(2,1,4);
        lifecycle.synchronize(p); assertEquals(Byte.valueOf((byte)2),p.getStatus());
        assertEquals(Byte.valueOf((byte)4),p.getProductSkus().get(1).getStatus());
    }
    @Test public void allThreeSaleStatesDriveAutomaticProductListing() {
        var lifecycle=new ProductLifecycleService(access(false));
        var p=product(0,1,4); lifecycle.synchronize(p); assertEquals(Byte.valueOf((byte)1),p.getStatus());
        p.getProductSkus().get(0).setStatus((byte)0); lifecycle.synchronize(p); assertEquals(Byte.valueOf((byte)0),p.getStatus());
        for (int state : new int[]{1,2,3}) {
            p=product(0,state,4,5); lifecycle.synchronize(p); assertEquals(Byte.valueOf((byte)1),p.getStatus());
            lifecycle.changeProduct(p,(byte)1,false); lifecycle.synchronize(p); assertEquals(Byte.valueOf((byte)1),p.getStatus());
            p.getProductSkus().get(0).setStatus((byte)0); lifecycle.synchronize(p); assertEquals(Byte.valueOf((byte)0),p.getStatus());
            assertEquals(Byte.valueOf((byte)5),p.getProductSkus().get(2).getStatus());
        }
    }
    @Test public void endingSkuClosesAtExpectedStockZeroIncludingInbound() {
        var lifecycle=new ProductLifecycleService(access(false));
        for (int outbound : new int[]{14,15,16}) {
            var p=product(1,3,4); var sku=p.getProductSkus().get(0);
            sku.setStock(10); sku.setOutboundQty(outbound); sku.setInboundQty(5);
            lifecycle.synchronize(p);
            assertEquals(Byte.valueOf((byte)(outbound>=15?0:3)),sku.getStatus());
            assertEquals(Byte.valueOf((byte)(outbound>=15?0:1)),p.getStatus());
            assertEquals(Byte.valueOf((byte)4),p.getProductSkus().get(1).getStatus());
        }
        var p=product(1,1,2,4); p.getProductSkus().forEach(s->{s.setStock(0);s.setOutboundQty(20);});
        lifecycle.synchronize(p); assertEquals(List.of((byte)1,(byte)2,(byte)4),p.getProductSkus().stream().map(ProductSku::getStatus).toList());
    }
    @Test public void permissionUsesLiveEmployeeAndPositionNotClientClaim() {
        var employee=new Employee(); employee.setEmployeeId(7); employee.setPositionId(3); employee.setEmployeeStatus((byte)1); employee.setEmployeeReviewStatus((byte)1);
        var position=new EmployeePosition(); position.setPositionCode("ADMIN"); position.setPositionStatus((byte)1);
        var employees=(EmployeeRepository)Proxy.newProxyInstance(EmployeeRepository.class.getClassLoader(),new Class<?>[]{EmployeeRepository.class},(p,m,a)->Optional.of(employee));
        var positions=(EmployeePositionRepository)Proxy.newProxyInstance(EmployeePositionRepository.class.getClassLoader(),new Class<?>[]{EmployeePositionRepository.class},(p,m,a)->Optional.of(position));
        var guard=new ProductStatusAccess(employees,positions); assertFalse(guard.canRestoreDiscontinued());
        var request=new MockHttpServletRequest(); request.getSession().setAttribute("loggedInEmployeeId",7); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertTrue(guard.canRestoreDiscontinued()); position.setPositionCode("STAFF"); assertFalse(guard.canRestoreDiscontinued());
        position.setPositionCode("ADMIN"); employee.setEmployeeStatus((byte)0); assertFalse(guard.canRestoreDiscontinued());
    }
    @Test public void editingRetiredSkuCannotBypassGuardByPostingDifferentStatus() {
        var p=product(0,4); var original=p.getProductSkus().get(0);
        var skuRepository=(ProductSkuRepository)Proxy.newProxyInstance(ProductSkuRepository.class.getClassLoader(),new Class<?>[]{ProductSkuRepository.class},(proxy,m,a)->Optional.of(original));
        var products=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,m,a)->Optional.of(p));
        var service=new ProductSkuService(); ReflectionTestUtils.setField(service,"repository",skuRepository); ReflectionTestUtils.setField(service,"products",products); ReflectionTestUtils.setField(service,"lifecycle",new ProductLifecycleService(access(false)));
        var form=new ProductSku(); form.setSkuId(1); form.setSkuName("attempt"); form.setStatus((byte)1);
        try { service.updateProductSku(form); fail(); } catch(ProductStatusAccessException expected) {}
        assertEquals(Byte.valueOf((byte)4),original.getStatus());
    }
    @Test public void preparedSkuNeverListsProductUntilProductIsExplicitlyListed() {
        var lifecycle=new ProductLifecycleService(access(false));var p=product(0,5,0,4);
        lifecycle.synchronize(p);assertEquals(Byte.valueOf((byte)0),p.getStatus());
        lifecycle.changeProduct(p,(byte)1,false);
        assertEquals(List.of((byte)1,(byte)0,(byte)4),p.getProductSkus().stream().map(ProductSku::getStatus).toList());
        assertEquals(Byte.valueOf((byte)1),p.getStatus());
    }
    @Test public void preparedSkusStayPreparedWithSaleSkuButCloseWhenProductGoesOffline() {
        var lifecycle=new ProductLifecycleService(access(false));var p=product(0,2,5,4);
        lifecycle.changeProduct(p,(byte)1,false);assertEquals(Byte.valueOf((byte)5),p.getProductSkus().get(1).getStatus());
        lifecycle.changeProduct(p,(byte)0,false);
        assertEquals(List.of((byte)0,(byte)0,(byte)4),p.getProductSkus().stream().map(ProductSku::getStatus).toList());
        try { lifecycle.validateSkuChange((byte)4,(byte)5);fail(); } catch(ProductStatusAccessException expected) {}
    }
    @Test public void skuServiceListsParentForSaleStatesAndNeverForPreparedState() {
        var parent=product(0);
        var products=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,m,a)-> m.getName().equals("lockForStatus") ? Optional.of(parent) : parent);
        var skus=(ProductSkuRepository)Proxy.newProxyInstance(ProductSkuRepository.class.getClassLoader(),new Class<?>[]{ProductSkuRepository.class},(proxy,m,a)->Optional.of(parent.getProductSkus().get(0)));
        var service=new ProductSkuService(); ReflectionTestUtils.setField(service,"repository",skus); ReflectionTestUtils.setField(service,"products",products); ReflectionTestUtils.setField(service,"lifecycle",new ProductLifecycleService(access(false)));
        var sku=new ProductSku(); sku.setSkuId(1); sku.setSkuName("prepared"); sku.setStatus((byte)5); sku.setProduct(parent);
        service.addProductSku(sku); assertEquals(Byte.valueOf((byte)0),parent.getStatus()); assertEquals(Byte.valueOf((byte)5),sku.getStatus());
        var form=new ProductSku(); form.setSkuId(1); form.setSkuName("listed"); form.setStatus((byte)1);
        service.updateProductSku(form); assertEquals(Byte.valueOf((byte)1),parent.getStatus());
        form.setStatus((byte)2); service.updateProductSku(form); assertEquals(Byte.valueOf((byte)1),parent.getStatus());
        form.setStatus((byte)3); form.setStock(10); form.setOutboundQty(2); form.setInboundQty(1);
        service.updateProductSku(form); assertEquals(Byte.valueOf((byte)1),parent.getStatus());
        form.setStatus((byte)5); service.updateProductSku(form); assertEquals(Byte.valueOf((byte)0),parent.getStatus());
    }
}
