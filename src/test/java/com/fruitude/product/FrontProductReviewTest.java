package com.fruitude.product;
import com.fruitude.product.model.FrontCatalogService;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrontProductReviewTest {
    @Test public void invalidSkuAndPageSizeAreRejectedBeforeQuerying() {
        var service=new FrontCatalogService((org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate)null);
        for(int sku:new int[]{0,-1})try{service.read(1,0,20,"","latest",sku);fail();}catch(IllegalArgumentException expected){}
        for(int size:new int[]{0,21})try{service.read(1,0,size,"","latest",1);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void foreignOrHiddenSkuIsRejectedBeforeReadingAnyComments() {
        var jdbc=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource()) {
            @Override public <T>T queryForObject(String sql,java.util.Map<String,?> params,Class<T> type){
                assertTrue(sql.contains("s.product_id=:id AND s.sku_id=:sku"));assertTrue(sql.contains("s.status IN(1,2,3,4,6)"));
                assertEquals(Integer.valueOf(1),params.get("id"));assertEquals(Integer.valueOf(99),params.get("sku"));return type.cast(0);
            }
            @Override public java.util.Map<String,Object> queryForMap(String sql,java.util.Map<String,?> params){throw new AssertionError("Invalid SKU must not read comments");}
        };
        try{new FrontCatalogService(jdbc).read(1,0,20,"","latest",99);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void skuScopeAppliesToSummarySearchAndPaginationTogether() {
        var calls=new java.util.ArrayList<String>();
        var jdbc=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource()) {
            @Override public <T>T queryForObject(String sql,java.util.Map<String,?> params,Class<T> type){
                assertEquals(Integer.valueOf(7),params.get("sku"));calls.add(sql);
                if(type==Integer.class)return type.cast(1);
                assertTrue(sql.contains("s.sku_id=:sku"));assertTrue(sql.contains("d.comment_text LIKE :keyword"));
                assertEquals("%甜!%!_%",params.get("keyword"));return type.cast(1L);
            }
            @Override public java.util.Map<String,Object> queryForMap(String sql,java.util.Map<String,?> params){
                calls.add(sql);assertTrue(sql.contains("s.sku_id=:sku"));assertFalse(sql.contains("LIKE :keyword"));
                return java.util.Map.of("amount",2L,"average",4.5,"star1",0L,"star2",0L,"star3",0L,"star4",1L,"star5",1L);
            }
            @Override @SuppressWarnings("unchecked") public <T>java.util.List<T> query(String sql,java.util.Map<String,?> params,org.springframework.jdbc.core.RowMapper<T> mapper){
                calls.add(sql);assertTrue(sql.contains("s.sku_id=:sku"));assertTrue(sql.contains("ORDER BY d.comment_star DESC"));
                assertEquals(3,params.get("limit"));assertEquals(0L,params.get("offset"));
                return java.util.List.of((T)new FrontCatalogService.Review("e***9",5,"分享盒","甜",null));
            }
        };
        var result=new FrontCatalogService(jdbc).read(1,0,3,"甜%_","highest",7);
        assertEquals(4,calls.size());assertEquals(1,result.count());assertEquals(2,result.totalCount());assertEquals(4.5,result.average(),0);
        assertEquals(1,result.reviews().size());assertFalse(result.hasMore());
    }
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
