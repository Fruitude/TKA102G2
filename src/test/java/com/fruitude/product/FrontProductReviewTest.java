package com.fruitude.product;
import com.fruitude.product.model.ProductService;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrontProductReviewTest {
    @Test public void accountsAreMaskedBeforeBeingReturned() {
        assertEquals("e***9",ProductService.maskAccount("elvi9"));
        assertEquals("a*z",ProductService.maskAccount("az"));
        assertEquals("*",ProductService.maskAccount("a"));
        assertEquals("會員",ProductService.maskAccount(null));
        assertFalse(ProductService.maskAccount("private@example.com").contains("example"));
    }
    @Test public void rejectsUnknownSortAndOverlongSearchBeforeQueryingDatabase() {
        var service=new ProductService((org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate)null);
        try {service.read(1,0,20,"","bad SQL sort");fail();} catch(IllegalArgumentException expected) {}
        try {service.read(1,0,20,"x".repeat(201),"latest");fail();} catch(IllegalArgumentException expected) {}
    }
    @Test public void rejectsInvalidPagingBeforeQueryingDatabase() {
        var service=new ProductService((org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate)null);
        for(int[] args:new int[][]{{0,0,3},{1,-1,3},{1,0,21}}) {
            try {service.read(args[0],args[1],args[2]);fail("Invalid query accepted");} catch(IllegalArgumentException expected) {}
        }
    }
}
