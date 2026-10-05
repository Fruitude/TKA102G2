package com.fruitude.product;

import com.fruitude.product.controller.ProductController;
import com.fruitude.product.model.*;
import com.fruitude.vendor.model.*;
import java.util.*;
import org.junit.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ProductOverviewFiltersTest {
    @Test public void childCategoriesFollowParentAndFiltersReachPagedQuery() throws Exception {
        ProductCategory parent = category(1, null), child = category(2, parent);
        ProductCategory other = category(3, null), otherChild = category(4, other);
        List<List<Integer>> calls = new ArrayList<>();
        List<String> stockFilters = new ArrayList<>();
        List<List<Object>> reviews = new ArrayList<>();
        var controller = new ProductController();
        ReflectionTestUtils.setField(controller, "productCategorySvc", new ProductCategoryService() {
            @Override public List<ProductCategory> getAll() { return List.of(parent, child, other, otherChild); }
        });
        ReflectionTestUtils.setField(controller, "vendorSvc", new VendorService() {
            @Override public List<VendorVO> getAll() {
                var vendor = new VendorVO(); vendor.setVendorId(9); vendor.setVendorName("測試廠商");
                return List.of(vendor);
            }
        });
        ReflectionTestUtils.setField(controller, "productSvc", new ProductService() {
            @Override public Page<ProductOverview> getOverviewPage(int page, int size, String status,
                    Integer parentId, Integer categoryId, Integer vendorId, String stockFilter,
                    Integer minComments, Integer maxComments, String ratingFilter, String sortBy, String sortDirection) {
                stockFilters.add(stockFilter);
                reviews.add(Arrays.asList(minComments, maxComments, ratingFilter));
                calls.add(Arrays.asList(page, size, parentId, categoryId, vendorId));
                assertEquals("on", status);
                return new PageImpl<>(List.of(), PageRequest.of(page - 1, size), 43);
            }
        });
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var result = mvc.perform(get("/product/listAllProduct").param("size", "20")
            .param("statusFilter", "on").param("parentCategoryId", "1")
            .param("categoryId", "2").param("vendorId", "9")).andReturn().getModelAndView();
        assertNotNull(result);
        assertEquals(List.of(parent, other), result.getModel().get("parentCategoryListData"));
        assertEquals(List.of(child), result.getModel().get("childCategoryListData"));
        assertEquals(2, result.getModel().get("selectedCategoryId"));
        assertEquals(3, result.getModel().get("totalPages"));
        assertEquals(List.of(1, 20, 1, 2, 9), calls.get(0));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .param("parentCategoryId", "3").param("categoryId", "2"));
        assertEquals(Integer.valueOf(3), calls.get(1).get(2));
        assertNull("A child from the previous parent must be cleared", calls.get(1).get(3));
        var cookies = new jakarta.servlet.http.Cookie[]{
            new jakarta.servlet.http.Cookie("fruitudeProductParentCategory", "1"),
            new jakarta.servlet.http.Cookie("fruitudeProductCategory", "2"),
            new jakarta.servlet.http.Cookie("fruitudeProductVendor", "9")};
        var restored = mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .cookie(cookies)).andReturn().getModelAndView();
        assertEquals(1, restored.getModel().get("selectedParentCategoryId"));
        assertEquals(2, restored.getModel().get("selectedCategoryId"));
        assertEquals(9, restored.getModel().get("selectedVendorId"));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on").cookie(cookies)
            .param("parentCategoryId", "").param("categoryId", "").param("vendorId", ""));
        assertEquals(Arrays.asList(1, 10, null, null, null), calls.get(3));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .cookie(new jakarta.servlet.http.Cookie("fruitudeProductVendor", "invalid")));
        assertNull(calls.get(4).get(4));
        var stockResult = mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .cookie(new jakarta.servlet.http.Cookie("fruitudeProductStockFilter", "abnormal")))
            .andReturn().getModelAndView();
        assertEquals("abnormal", stockResult.getModel().get("stockFilter"));
        assertEquals("abnormal", stockFilters.get(5));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on").param("stockFilter", "all")
            .cookie(new jakarta.servlet.http.Cookie("fruitudeProductStockFilter", "abnormal")));
        assertEquals("all", stockFilters.get(6));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on").param("stockFilter", "invalid"));
        assertEquals("all", stockFilters.get(7));
        var reviewCookies = new jakarta.servlet.http.Cookie[]{
            new jakarta.servlet.http.Cookie("fruitudeProductMinComments", "0"),
            new jakarta.servlet.http.Cookie("fruitudeProductMaxComments", "100"),
            new jakarta.servlet.http.Cookie("fruitudeProductRatingFilter", "4-5")};
        var reviewResult = mvc.perform(get("/product/listAllProduct").param("statusFilter", "on").cookie(reviewCookies))
            .andReturn().getModelAndView();
        assertEquals(Arrays.asList(0, 100, "4-5"), reviews.get(8));
        assertEquals("4-5", reviewResult.getModel().get("ratingFilter"));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on").cookie(reviewCookies)
            .param("minComments", "").param("maxComments", "").param("ratingFilter", ""));
        assertEquals(Arrays.asList(null, null, ""), reviews.get(9));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .param("minComments", "-1").param("maxComments", "2147483648").param("ratingFilter", "invalid"));
        assertEquals(Arrays.asList(null, null, ""), reviews.get(10));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .param("minComments", "12").param("ratingFilter", "unrated"));
        assertEquals(Arrays.asList(12, null, "unrated"), reviews.get(11));
    }

    private ProductCategory category(int id, ProductCategory parent) {
        var category = new ProductCategory();
        category.setProductCategoryId(id);
        category.setCategoryName("分類" + id);
        category.setParentCategory(parent);
        return category;
    }
}
