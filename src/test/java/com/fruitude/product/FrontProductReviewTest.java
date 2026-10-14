package com.fruitude.product;
import com.fruitude.product.model.FrontCatalogService;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrontProductReviewTest {
    @Test public void accountsAreMaskedBeforeBeingReturned() {
        assertEquals("e***9",FrontCatalogService.maskAccount("elvi9"));
        assertEquals("a*z",FrontCatalogService.maskAccount("az"));
        assertEquals("*",FrontCatalogService.maskAccount("a"));
        assertEquals("會員",FrontCatalogService.maskAccount(null));
        assertFalse(FrontCatalogService.maskAccount("private@example.com").contains("example"));
    }
    @Test public void rejectsUnknownSortAndOverlongSearchBeforeQueryingDatabase() {
        var service=new FrontCatalogService((org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate)null);
        try {service.read(1,0,20,"","bad SQL sort");fail();} catch(IllegalArgumentException expected) {}
        try {service.read(1,0,20,"x".repeat(201),"latest");fail();} catch(IllegalArgumentException expected) {}
    }
    @Test public void rejectsInvalidPagingBeforeQueryingDatabase() {
        var service=new FrontCatalogService((org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate)null);
        for(int[] args:new int[][]{{0,0,3},{1,-1,3},{1,0,21}}) {
            try {service.read(args[0],args[1],args[2]);fail("Invalid query accepted");} catch(IllegalArgumentException expected) {}
        }
    }
}
