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
    private org.springframework.test.web.servlet.MockMvc nextProductMvc(java.util.concurrent.atomic.AtomicInteger saves,boolean duplicate) {
        return nextProductMvc(saves,duplicate,new java.util.concurrent.atomic.AtomicReference<Product>());
    }
    private org.springframework.test.web.servlet.MockMvc nextProductMvc(java.util.concurrent.atomic.AtomicInteger saves,boolean duplicate,java.util.concurrent.atomic.AtomicReference<Product> captured) {
        var controller=new ProductController();var vendor=new VendorVO();vendor.setVendorId(9);vendor.setVendorName("測試供應商");
        var category=category(2,null);
        ReflectionTestUtils.setField(controller,"vendorSvc",new VendorService(){
            @Override public List<VendorVO> getAll(){return List.of(vendor);}
            @Override public VendorVO getOneVendor(Integer id){return id==9?vendor:null;}
        });
        ReflectionTestUtils.setField(controller,"productCategorySvc",new ProductCategoryService(){
            @Override public List<ProductCategory> getAll(){return List.of(category);}
            @Override public ProductCategory getOneProductCategory(Integer id){return id==2?category:null;}
        });
        ReflectionTestUtils.setField(controller,"productSvc",new ProductService(){
            @Override public boolean isNameUsed(Integer id,String name,Integer excluded){return duplicate;}
            @Override public void addProduct(Product product,boolean activate){saves.incrementAndGet();product.setProductId(99);captured.set(product);}
        });
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder nextProductRequest(boolean next,String... fields) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/product/insert")
            .param("productName","愛文芒果").param("productDesc","當季鮮果").param("vendor.vendorId","9")
            .param("productCategory.productCategoryId","2").param("status","0")
            .param("productSkus[0].skuName","六顆裝").param("productSkus[0].anotherName","小盒")
            .param("productSkus[0].price","123").param("productSkus[0].stock","50")
            .param("productSkus[0].safetyStock","10").param("productSkus[0].status","0")
            .param("createNext",String.valueOf(next)).param("carryFields",fields);
    }


    @Test public void createBindsAllQuantityFieldsAndPreservesMultipleImageOrder() throws Exception {
        var saves=new java.util.concurrent.atomic.AtomicInteger();var captured=new java.util.concurrent.atomic.AtomicReference<Product>();
        var mvc=nextProductMvc(saves,false,captured);
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var request=nextProductRequest(false).file(new org.springframework.mock.web.MockMultipartFile("skuImages[0]","second.png","image/png",bytes.toByteArray()))
            .file(new org.springframework.mock.web.MockMultipartFile("skuImages[0]","first.png","image/png",bytes.toByteArray()))
            .param("productSkus[0].inboundQty","17").param("productSkus[0].outboundQty","9")
            .param("productSkus[0].maxBackorderQty","31").param("productSkus[0].purchaseAddOnQty","47");
        assertEquals("/product/listAllProduct",mvc.perform(request).andReturn().getResponse().getRedirectedUrl());
        assertEquals(1,saves.get());var sku=captured.get().getProductSkus().get(0);
        assertEquals(Integer.valueOf(17),sku.getInboundQty());assertEquals(Integer.valueOf(9),sku.getOutboundQty());
        assertEquals(Integer.valueOf(31),sku.getMaxBackorderQty());assertEquals(Integer.valueOf(47),sku.getPurchaseAddOnQty());
        assertEquals(List.of("second.png","first.png"),sku.getProductImages().stream().map(ProductImage::getImageName).toList());
        assertEquals(List.of(0,1),sku.getProductImages().stream().map(ProductImage::getSortOrder).toList());
        assertSame(sku,sku.getProductImages().get(1).getProductSku());
    }
    @Test public void createRejectsInvalidPricesNamesQuantitiesAndImagesBeforeSaving() throws Exception {
        var saves=new java.util.concurrent.atomic.AtomicInteger();var mvc=nextProductMvc(saves,false);
        for(var invalid:List.of(new String[]{"productSkus[0].price","10000001"},new String[]{"productSkus[0].price","0"},
                new String[]{"productSkus[0].price","1.5"},new String[]{"productSkus[0].price",""},
                new String[]{"productName","   "},new String[]{"productSkus[0].skuName","   "},
                new String[]{"productSkus[0].inboundQty","-1"},new String[]{"productSkus[0].outboundQty","-1"},
                new String[]{"productSkus[0].maxBackorderQty","-1"},new String[]{"productSkus[0].purchaseAddOnQty","-1"},
                new String[]{"productSkus[0].purchaseAddOnQty","2147483648"})) {
            var result=mvc.perform(nextProductRequest(false).with(request->{request.setParameter(invalid[0],invalid[1]);return request;})).andReturn();
            assertNull(java.util.Arrays.toString(invalid),result.getResponse().getRedirectedUrl());
            assertEquals("admin/productmanagement/product/addProduct",result.getModelAndView().getViewName());
        }
        var invalidImage=mvc.perform(nextProductRequest(false).file(new org.springframework.mock.web.MockMultipartFile("skuImages[0]","bad.png","image/png",new byte[]{1,2,3}))).andReturn();
        assertNull(invalidImage.getResponse().getRedirectedUrl());assertNotNull(invalidImage.getModelAndView().getModel().get("errorMessage"));
        var oversized=mvc.perform(nextProductRequest(false).file(new org.springframework.mock.web.MockMultipartFile("skuImages[0]","large.png","image/png",new byte[5*1024*1024+1]))).andReturn();
        assertEquals("圖片不可超過 5 MB",oversized.getModelAndView().getModel().get("errorMessage"));
        assertEquals(0,saves.get());
    }
    @Test public void createAcceptsMaximumPriceAndAllQuantityBoundaries() throws Exception {
        var saves=new java.util.concurrent.atomic.AtomicInteger();var mvc=nextProductMvc(saves,false);
        var request=nextProductRequest(false).with(value->{value.setParameter("productSkus[0].price","10000000");return value;})
            .param("productSkus[0].inboundQty","2147483647").param("productSkus[0].outboundQty","0")
            .param("productSkus[0].maxBackorderQty","0").param("productSkus[0].purchaseAddOnQty","2147483647");
        assertEquals("/product/listAllProduct",mvc.perform(request).andReturn().getResponse().getRedirectedUrl());assertEquals(1,saves.get());
    }

    @Test public void createNextCarriesOnlySelectedTextFieldsAfterSuccessfulSave() throws Exception {
        var saves=new java.util.concurrent.atomic.AtomicInteger();var mvc=nextProductMvc(saves,false);
        var result=mvc.perform(nextProductRequest(true,"vendor","category","name","description","skuName","anotherName")).andReturn();
        assertEquals(1,saves.get());assertEquals("/product/addProduct",result.getResponse().getRedirectedUrl());
        var next=mvc.perform(get("/product/addProduct").flashAttrs(result.getFlashMap())).andReturn().getModelAndView();
        var product=(Product)next.getModel().get("product");
        assertEquals(Integer.valueOf(9),product.getVendor().getVendorId());assertEquals(Integer.valueOf(2),product.getProductCategory().getProductCategoryId());
        assertEquals("愛文芒果",product.getProductName());assertEquals("當季鮮果",product.getProductDesc());assertNull(product.getProductId());
        var sku=product.getProductSkus().get(0);assertEquals("六顆裝",sku.getSkuName());assertEquals("小盒",sku.getAnotherName());
        assertNull(sku.getPrice());assertEquals(Integer.valueOf(0),sku.getStock());assertEquals(Byte.valueOf((byte)0),sku.getStatus());assertTrue(sku.getProductImages().isEmpty());
        var partial=mvc.perform(nextProductRequest(true,"category","anotherName")).andReturn();
        var partialPage=mvc.perform(get("/product/addProduct").flashAttrs(partial.getFlashMap())).andReturn().getModelAndView();
        var draft=(Product)partialPage.getModel().get("product");assertNull(draft.getVendor());assertNull(draft.getProductName());assertNull(draft.getProductDesc());
        assertNull(draft.getProductSkus().get(0).getSkuName());assertEquals("小盒",draft.getProductSkus().get(0).getAnotherName());
    }

    @Test public void failedCreateDoesNotAdvanceAndNormalCreateKeepsOriginalDestination() throws Exception {
        var saves=new java.util.concurrent.atomic.AtomicInteger();var invalid=nextProductMvc(saves,true);
        var failed=invalid.perform(nextProductRequest(true,"name")).andReturn();
        assertEquals(0,saves.get());assertNull(failed.getResponse().getRedirectedUrl());assertTrue(failed.getFlashMap().isEmpty());
        assertEquals("admin/productmanagement/product/addProduct",failed.getModelAndView().getViewName());
        var normal=nextProductMvc(saves,false).perform(nextProductRequest(false,"name")).andReturn();
        assertEquals("/product/listAllProduct",normal.getResponse().getRedirectedUrl());assertFalse(normal.getFlashMap().containsKey("nextProductDraft"));assertTrue(normal.getFlashMap().containsKey("success"));
    }

    @Test public void productNameCheckIsScopedToVendorAndRejectsInvalidInput() throws Exception {
        var controller=new ProductController();var calls=new ArrayList<String>();
        ReflectionTestUtils.setField(controller,"productSvc",new ProductService(){
            @Override public boolean isNameUsed(Integer vendorId,String name,Integer excludedId){
                calls.add(vendorId+":"+name);return vendorId==9 && name.equals("愛文芒果");
            }
        });
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        var duplicate=mvc.perform(get("/product/name-availability").param("vendorId","9").param("name"," 愛文芒果 ")).andReturn().getResponse();
        assertEquals(200,duplicate.getStatus());assertEquals("no-store",duplicate.getHeader("Cache-Control"));
        assertTrue(duplicate.getContentAsString().contains("\"exists\":true"));
        var other=mvc.perform(get("/product/name-availability").param("vendorId","10").param("name","愛文芒果")).andReturn().getResponse();
        assertTrue(other.getContentAsString().contains("\"exists\":false"));
        assertEquals(400,mvc.perform(get("/product/name-availability").param("vendorId","0").param("name","愛文芒果")).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/product/name-availability").param("vendorId","9").param("name"," ")).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/product/name-availability").param("vendorId","9").param("name","果".repeat(101))).andReturn().getResponse().getStatus());
        assertEquals(List.of("9:愛文芒果","10:愛文芒果"),calls);
    }

    @Test public void childCategoriesFollowParentAndFiltersReachPagedQuery() throws Exception {
        ProductCategory parent = category(1, null), child = category(2, parent);
        ProductCategory other = category(3, null), otherChild = category(4, other);
        List<List<Integer>> calls = new ArrayList<>();
        List<String> stockFilters = new ArrayList<>();
        List<List<Object>> searchCalls = new ArrayList<>();
        List<List<Object>> reviews = new ArrayList<>();
        List<String> origins = new ArrayList<>();
        var controller = new ProductController();
        ReflectionTestUtils.setField(controller, "productCategorySvc", new ProductCategoryService() {
            @Override public List<ProductCategory> getAll() { return List.of(parent, child, other, otherChild); }
        });
        ReflectionTestUtils.setField(controller, "vendorSvc", new VendorService() {
            @Override public List<VendorVO> getAll() {
                var vendor = new VendorVO(); vendor.setVendorId(9); vendor.setVendorName("測試廠商"); vendor.setOrigin("台灣台南");
                return List.of(vendor);
            }
        });
        ReflectionTestUtils.setField(controller, "productSvc", new ProductService() {
            @Override public List<FrontCatalogService.SearchOption> getOverviewOrigins() {
                return FrontCatalogService.originOptions(List.of("台灣台南"));
            }
            @Override public Page<com.fruitude.product.model.ProductRepository.ProductOverview> getOverviewPage(int page, int size, String status,
                    Integer parentId, Integer categoryId, Integer vendorId, String stockFilter,
                    Integer minComments, Integer maxComments, String ratingFilter, String sortBy, String sortDirection,
                    String keyword, boolean searchProductName, boolean searchSkuName, String origin) {
                stockFilters.add(stockFilter); origins.add(origin);
                searchCalls.add(Arrays.asList(keyword, searchProductName, searchSkuName));
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
        assertEquals(List.of("", true, true), searchCalls.get(0));
        var searched = mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .param("keyword", "草莓").param("searchProductName", "false").param("searchSkuName", "true"))
            .andReturn().getModelAndView();
        assertEquals(List.of("草莓", false, true), searchCalls.get(1));
        assertEquals("草莓", searched.getModel().get("keyword"));
        assertEquals(false, searched.getModel().get("searchProductName"));
        calls.remove(1); stockFilters.remove(1); reviews.remove(1); searchCalls.remove(1);
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
        var fullwidth = mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .param("minComments", "１２").param("maxComments", "1００"))
            .andReturn().getModelAndView();
        assertEquals(Arrays.asList(12, 100, ""), reviews.get(12));
        assertEquals(12, fullwidth.getModel().get("minComments"));
        assertEquals(100, fullwidth.getModel().get("maxComments"));
        mvc.perform(get("/product/listAllProduct").param("statusFilter", "on")
            .param("minComments", "１２a").param("maxComments", "２１４７４８３６４８"));
        assertEquals(Arrays.asList(null, null, ""), reviews.get(13));
        var originResult = mvc.perform(get("/product/listAllProduct").param("statusFilter","on").param("origin","台灣台南"))
            .andReturn().getModelAndView();
        assertEquals("台灣台南",origins.get(origins.size()-1));
        assertEquals(List.of(new FrontCatalogService.SearchOption("台灣台南","台南")),originResult.getModel().get("originListData"));
        assertEquals("台灣台南",originResult.getModel().get("selectedOrigin"));
        var originCookie = new jakarta.servlet.http.Cookie("fruitudeProductOrigin","%E5%8F%B0%E7%81%A3%E5%8F%B0%E5%8D%97");
        mvc.perform(get("/product/listAllProduct").param("statusFilter","on").cookie(originCookie));
        assertEquals("台灣台南",origins.get(origins.size()-1));
        mvc.perform(get("/product/listAllProduct").param("statusFilter","on").param("origin","").cookie(originCookie));
        assertEquals("",origins.get(origins.size()-1));

    }

    private ProductCategory category(int id, ProductCategory parent) {
        var category = new ProductCategory();
        category.setProductCategoryId(id);
        category.setCategoryName("分類" + id);
        category.setParentCategory(parent);
        return category;
    }
}
