package com.fruitude.product;

import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.fruitude.product.model.*;
import com.fruitude.product.controller.FrontProductController;

public class FrontCatalogTest {
    @Test public void discountedOrderableSkuIsPreferredOverCheaperRegularSku() throws Exception {
        var p=product(93,"活動水果");sku(p,930,100,1);sku(p,931,300,1);sku(p,932,200,1);sku(p,933,500,4);
        var catalog=service(List.of(p));
        org.springframework.test.util.ReflectionTestUtils.setField(catalog,"promoPriceService",new com.fruitude.promo.model.PromoPriceService(){
            @Override public Map<Integer,Integer> findActivePrices(Collection<Integer> ids){return Map.of(931,250,932,150,933,50);}
        });
        var view=catalog.getProducts().get(0);
        assertEquals(Integer.valueOf(932),view.skuId());assertEquals(Integer.valueOf(150),view.price());
        String html=mvc(catalog).perform(get("/front/"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("活動優惠"));assertTrue(html.contains("class=\"home-promo-bow\""));
        org.springframework.test.util.ReflectionTestUtils.setField(catalog,"promoPriceService",null);
        assertEquals(Integer.valueOf(930),catalog.getProducts().get(0).skuId());
    }
    @Test public void catalogBatchesKeepOrderAndDoNotReloadDatabase() throws Exception {
        var products=new ArrayList<FrontCatalogService.ProductView>();
        for(int id=1;id<=45;id++) products.add(new FrontCatalogService.ProductView(id,"水果"+id,"",100,100,
            id,null,false,List.of(new FrontCatalogService.SkuView(id,"小盒",100,100,10,null,List.of(),1)),10,0d));
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        var catalog=new FrontCatalogService(){
            @Override public List<ProductView> getAllProducts(Integer member,Collection<Integer> favorites){
                reads.incrementAndGet();return List.copyOf(products);
            }
        };
        var client=mvc(catalog);var session=new org.springframework.mock.web.MockHttpSession();
        var first=client.perform(get("/front/all-products/").session(session)).andReturn().getResponse();
        String html=first.getContentAsString();
        assertEquals(20,countCards(html));assertFalse(html.contains("data-product-id=\"21\""));
        var tokenMatch=java.util.regex.Pattern.compile("data-token=\"([^\"]+)\"").matcher(html);
        assertTrue(tokenMatch.find());String token=tokenMatch.group(1);
        for(int offset:new int[]{20,40,20}) {
            var response=client.perform(get("/front/api/catalog-batch").session(session)
                .param("token",token).param("offset",String.valueOf(offset))).andReturn().getResponse();
            assertEquals(200,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
            assertEquals(offset==40?5:20,countCards(response.getContentAsString()));
            assertTrue(response.getContentAsString().contains("data-product-id=\""+(offset+1)+"\""));
            assertEquals(String.valueOf(Math.min(45,offset+20)),response.getHeader("X-Catalog-Next"));
        }
        assertEquals(1,reads.get());
        assertEquals(410,client.perform(get("/front/api/catalog-batch").session(session)
            .param("token","unknown").param("offset","20")).andReturn().getResponse().getStatus());
        assertEquals(400,client.perform(get("/front/api/catalog-batch").session(session)
            .param("token",token).param("offset","-1")).andReturn().getResponse().getStatus());
        session.setAttribute("loggedInMemberId",101);
        assertEquals(410,client.perform(get("/front/api/catalog-batch").session(session)
            .param("token",token).param("offset","20")).andReturn().getResponse().getStatus());
    }
    private int countCards(String html) {
        var matcher=java.util.regex.Pattern.compile("data-product-id=\"[0-9]+\"").matcher(html);
        int count=0;while(matcher.find())count++;return count;
    }

    @Test public void supplierDetailsUseExistingVendorAndHideMissingLogo() throws Exception {
        var p=product(92,"產地水果");sku(p,920,100,1);
        var vendor=new com.fruitude.vendor.model.VendorVO();vendor.setVendorId(42);
        vendor.setIsActive((byte)1);
        vendor.setVendorName("測試果園");vendor.setOrigin("臺灣臺南");vendor.setLogo(new byte[]{1});p.setVendor(vendor);
        var html=mvc(service(List.of(p))).perform(get("/front/product/view/").param("productId","92"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("測試果園"));assertTrue(html.contains("臺南"));assertFalse(html.contains("臺灣臺南"));
        assertTrue(html.contains("/vendor/DBGifReader?vendorId=42"));
        assertTrue(html.contains("href=\"/front/farmers/#farmer-2\""));
        assertTrue(html.contains("供應："));assertFalse(html.contains("供應廠商："));
        assertTrue(html.indexOf("pv-supplier-info")>html.indexOf("id=\"pv-description\""));
        assertTrue(html.contains(">產地水果評價</h2>"));
        vendor.setLogo(null);vendor.setIsActive((byte)0);vendor.setOrigin("日本青森");
        html=mvc(service(List.of(p))).perform(get("/front/product/view/").param("productId","92"))
            .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("class=\"pv-supplier-logo\""));assertTrue(html.contains("測試果園"));
        assertTrue(html.contains("日本青森"));assertFalse(html.contains("class=\"pv-supplier-link\""));
    }
    @Test public void reviewSkuButtonsAppearInBothPlacesAndIncludeStoppingSupply() throws Exception {
        var p=product(90,"評論規格水果");sku(p,900,100,1).setSkuName("正常小盒");
        sku(p,901,200,4).setSkuName("售完中盒");sku(p,902,300,6).setSkuName("最後一批");
        sku(p,903,400,0).setSkuName("下架大盒");sku(p,904,500,7).setSkuName("永久停產盒");
        var html=mvc(service(List.of(p))).perform(get("/front/product/view/").param("productId","90")).andReturn().getResponse().getContentAsString();
        for(int id:new int[]{900,901,902})assertEquals(2,html.split("data-review-sku=\""+id+"\"",-1).length-1);
        assertFalse(html.contains("data-review-sku=\"903\""));assertFalse(html.contains("data-review-sku=\"904\""));
        assertEquals(2,html.split("全部規格",-1).length-1);
        assertTrue(html.indexOf("data-review-sku=\"900\"",html.indexOf("<dialog"))<html.indexOf("id=\"pv-reviews-search\""));
    }
    @Test public void oneVisibleSkuDoesNotShowReviewFilterButtons() throws Exception {
        var p=product(91,"單規格水果");sku(p,910,100,1);sku(p,911,200,0);sku(p,912,300,7);
        var html=mvc(service(List.of(p))).perform(get("/front/product/view/").param("productId","91")).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("data-review-sku="));assertFalse(html.contains("全部規格"));
    }
    @Test public void reviewApiForwardsSkuAndPreviewSize() throws Exception {
        var catalog=new FrontCatalogService(){
            @Override public ReviewPage read(int id,int page,int size,String keyword,String sort,Integer skuId){
                assertEquals(90,id);assertEquals(2,page);assertEquals(3,size);assertEquals("甜",keyword);assertEquals("highest",sort);assertEquals(Integer.valueOf(902),skuId);
                return ReviewPage.empty();
            }
        };
        var response=mvc(catalog).perform(get("/front/api/product-reviews").param("productId","90").param("page","2")
            .param("size","3").param("keyword","甜").param("sort","highest").param("skuId","902")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    @Test public void farmerPageKeepsMainBranchStoriesAndVendorProducts() {
        var fruit=new FrontCatalogService.ProductView(1,"小農水果","",100,100,1,null,false,List.of(),10,0d);
        var catalog=new FrontCatalogService(){
            @Override public Map<Integer,List<ProductView>> getProductsByVendorIds(List<Integer> ids){
                assertEquals(List.of(42,43),ids);return Map.of(42,List.of(fruit),43,List.of());
            }
        };
        var farmers=new ArrayList<com.fruitude.farmer.model.FarmerVO>();
        for(int id:List.of(42,43)) {
            var vendor=new com.fruitude.vendor.model.VendorVO();vendor.setVendorId(id);vendor.setContactPerson("小農"+id);
            vendor.setVendorIntroduction("供應商介紹");
            var farmer=new com.fruitude.farmer.model.FarmerVO();farmer.setVendorId(id);farmer.setVendor(vendor);
            farmer.setFarmerStory(id==42?"第一段\n\n 第二段 ":null);farmers.add(farmer);
        }
        var controller=new FrontProductController(catalog,noPromos(),new com.fruitude.orders.model.OrdersService());
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"farmerService",new com.fruitude.farmer.model.FarmerService(){
            @Override public List<com.fruitude.farmer.model.FarmerVO> getFarmersForFront(){return farmers;}
        });
        var model=new org.springframework.ui.ExtendedModelMap();var response=new org.springframework.mock.web.MockHttpServletResponse();
        assertEquals("front/farmers/index",controller.farmers(model,response));assertEquals("no-store",response.getHeader("Cache-Control"));
        @SuppressWarnings("unchecked") var views=(List<FrontProductController.FarmerView>)model.get("farmers");
        assertEquals(List.of("第一段","第二段"),views.get(0).paragraphs());assertEquals(List.of(fruit),views.get(0).products());
        assertEquals(List.of("供應商介紹"),views.get(1).paragraphs());assertTrue(views.get(1).products().isEmpty());
    }
    @Test public void vendorCatalogKeepsEmptyVendorsAndGroupsOnlySellableProducts() {
        var row=new com.fruitude.product.model.VendorProductRow(){public Integer getVendorId(){return 42;}public Integer getProductId(){return 1;}};
        var repository=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(p,m,a)->{
            if(m.getName().equals("findFrontRows"))return List.of(projection(ProductRepository.FrontCatalogRow.class,key->switch(key){
                case "getProductId","getSkuId","getSkuStatus" -> 1;case "getName","getSkuName" -> "小農水果";
                case "getPrice" -> 100;case "getStock" -> 10;default -> null;
            }));
            if(m.getName().equals("findProductIdsByVendorIds"))return List.of(row);
            return List.of();
        });
        var catalog=new FrontCatalogService(repository);var grouped=catalog.getProductsByVendorIds(List.of(42,43));
        assertEquals(Integer.valueOf(1),grouped.get(42).get(0).productId());assertTrue(grouped.get(43).isEmpty());
        assertTrue(catalog.getProductsByVendorIds(List.of()).isEmpty());
    }
    private FrontCatalogService service(List<Product> products) {
        ProductRepository repo = (ProductRepository) Proxy.newProxyInstance(ProductRepository.class.getClassLoader(), new Class<?>[]{ProductRepository.class}, (proxy, method, args) -> {
            if (method.getName().equals("findFrontRows")) {
                List<com.fruitude.product.model.ProductRepository.FrontCatalogRow> rows = new ArrayList<>();
                for (Product p : products) if (args[0] == null || args[0].equals(p.getProductId()))
                    for (ProductSku s : p.getProductSkus()) if (((s.getStatus() >= 1 && s.getStatus() <= 3) || s.getStatus()==4 || s.getStatus()==6) && s.getPrice() != null && s.getPrice() > 0)
                        rows.add(projection(com.fruitude.product.model.ProductRepository.FrontCatalogRow.class, key -> switch (key) {
                            case "getProductId" -> p.getProductId(); case "getName" -> p.getProductName(); case "getDescription" -> p.getProductDesc();
                            case "getCategoryId" -> p.getProductCategory() == null ? null : p.getProductCategory().getProductCategoryId();
                            case "getSkuId" -> s.getSkuId(); case "getSkuName" -> s.getSkuName(); case "getAnotherName" -> s.getAnotherName(); case "getPrice" -> s.getPrice(); case "getSkuStatus" -> (int)s.getStatus(); case "getStock" -> s.getStock(); case "getInboundQty" -> s.getInboundQty(); case "getOutboundQty" -> s.getOutboundQty(); case "getMaxBackorderQty" -> s.getMaxBackorderQty(); default -> null;
                        }));
                return rows;
            }
            if (method.getName().equals("findFrontImages")) {
                List<com.fruitude.product.model.ProductRepository.FrontImageRow> rows = new ArrayList<>();
                for (Product p : products) if (((List<?>)args[0]).contains(p.getProductId()))
                    for (ProductSku s : p.getProductSkus()) if (((s.getStatus() >= 1 && s.getStatus() <= 3) || s.getStatus()==4 || s.getStatus()==6) && s.getPrice() != null && s.getPrice() > 0)
                        for (ProductImage i : s.getProductImages().stream().filter(i -> i.getImageData() != null && i.getImageData().length > 0)
                            .sorted(Comparator.comparing((ProductImage i) -> i.getSortOrder() == null ? 0 : i.getSortOrder()).thenComparing(ProductImage::getImageId)).toList())
                            rows.add(projection(com.fruitude.product.model.ProductRepository.FrontImageRow.class, key -> key.equals("getSkuId") ? s.getSkuId() : i.getImageId()));
                return rows;
            }
            if (method.getName().equals("findFrontCategories")) {
                Map<Integer, com.fruitude.product.model.ProductRepository.FrontCategoryRow> rows = new HashMap<>();
                for (Product p : products) {
                    Set<Integer> visited = new HashSet<>();
                    for (ProductCategory c = p.getProductCategory(); c != null && visited.add(c.getProductCategoryId()); c = c.getParentCategory()) {
                        final ProductCategory category = c;
                        rows.put(c.getProductCategoryId(), projection(com.fruitude.product.model.ProductRepository.FrontCategoryRow.class, key -> switch (key) {
                            case "getCategoryId" -> category.getProductCategoryId(); case "getName" -> category.getCategoryName();
                            case "getParentId" -> category.getParentCategory() == null ? null : category.getParentCategory().getProductCategoryId(); default -> null;
                        }));
                    }
                }
                return new ArrayList<>(rows.values());
            }
            if (method.getName().equals("findBestSellerProductIds") || method.getName().equals("findTopRatedProductIds"))
                return products.stream().map(Product::getProductId).toList();
            if (method.getName().equals("findCatalogPriorities")) return List.of();
            if (method.getName().equals("findProductSupplier")) {
                var vendor = products.stream().filter(p -> p.getProductId().equals(args[0]))
                    .map(Product::getVendor).filter(Objects::nonNull).findFirst().orElse(null);
                return vendor == null ? null : projection(ProductRepository.ProductSupplierRow.class, key -> switch (key) {
                    case "getVendorId" -> vendor.getVendorId(); case "getName" -> vendor.getVendorName();
                    case "getOrigin" -> vendor.getOrigin();
                    case "getFarmerPosition" -> Byte.valueOf((byte)1).equals(vendor.getIsActive()) ? 2 : 0;
                    case "getHasLogo" -> vendor.getLogo() != null && vendor.getLogo().length > 0 ? 1 : 0;
                    default -> null;
                });
            }
            throw new AssertionError(method.getName());
        });
        return new FrontCatalogService(repo) {
            @Override public ReviewPage read(int productId,int page,int size) { return ReviewPage.empty(); }
        };
    }
    @SuppressWarnings("unchecked")
    private <T> T projection(Class<T> type, java.util.function.Function<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> values.apply(m.getName()));
    }
    private Product product(int id, String name) { Product p = new Product(); p.setProductId(id); p.setProductName(name); p.setStatus((byte)1); p.setProductDesc("資料庫描述"); return p; }
    private ProductSku sku(Product p, int id, int price, int status) { ProductSku s = new ProductSku(); s.setSkuId(id); s.setSkuName("規格" + id); s.setPrice(price); s.setStock(10); s.setStatus((byte)status); s.setProduct(p); p.getProductSkus().add(s); return s; }
    private void image(ProductSku s, int id, int order) { ProductImage i = new ProductImage(); i.setImageId(id); i.setSortOrder(order); i.setImageData(new byte[]{1}); i.setProductSku(s); s.getProductImages().add(i); }
    // 首頁會向 PromoService 要活動；這些測試只看商品，所以給一個沒有任何進行中活動的版本（不連資料庫）
    private com.fruitude.promo.model.PromoService noPromos() {
        return new com.fruitude.promo.model.PromoService() {
            @Override public com.fruitude.promo.model.HomePromos findHomePromos(com.fruitude.promo.model.MemberPromoState state) {
                return new com.fruitude.promo.model.HomePromos(List.of(), List.of());
            }
        };
    }
    private MockMvc mvc(FrontCatalogService service) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver view = new ThymeleafViewResolver(); view.setTemplateEngine(engine); view.setCharacterEncoding("UTF-8");
        var controller=new FrontProductController(service,noPromos(),new com.fruitude.orders.model.OrdersService());
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"imageCache",new com.fruitude.product.model.ProductImageService());
        return MockMvcBuilders.standaloneSetup(controller).setViewResolvers(view).build();
    }
    @Test public void pricesImagesAndVisibilityComeFromActiveSkus() {
        Product p = product(1, "測試芒果"); sku(p, 1, 1, 0); ProductSku available = sku(p, 2, 200, 1); image(available, 9, 2); image(available, 10, 1);
        Product noActiveSku = product(2, "隱藏商品"); sku(noActiveSku, 3, 50, 0);
        List<FrontCatalogService.ProductView> result = service(List.of(p, noActiveSku)).getProducts();
        assertEquals(1, result.size()); assertEquals(Integer.valueOf(200), result.get(0).price()); assertEquals(Integer.valueOf(2), result.get(0).skuId()); assertEquals(Integer.valueOf(10), result.get(0).imageId());
        assertFalse(result.get(0).giftBox());
        assertEquals(List.of(10, 9), result.get(0).skus().get(0).imageIds());
    }
    @Test public void giftClassificationUsesCategoryNotPrice() {
        Product p = product(1, "便宜水果"); sku(p, 1, 10, 1);
        ProductCategory parent = new ProductCategory(); parent.setProductCategoryId(1); parent.setCategoryName("精選禮盒");
        ProductCategory child = new ProductCategory(); child.setProductCategoryId(2); child.setCategoryName("小盒"); child.setParentCategory(parent); p.setProductCategory(child);
        assertTrue(service(List.of(p)).getProducts().get(0).giftBox());
    }
    @Test public void giftPackagingInSkuNameIsRecognized() {
        Product p = product(1, "愛文芒果"); ProductSku s = sku(p, 1, 799, 1); s.setSkuName("愛文芒果6顆禮盒");
        assertTrue(service(List.of(p)).getProducts().get(0).giftBox());
        s.setStatus((byte)0); sku(p, 2, 100, 1);
        assertFalse(service(List.of(p)).getProducts().get(0).giftBox());
    }
    @Test public void homeRendersDatabaseCardsAndContextAwareLinks() throws Exception {
        Product p = product(5, "資料庫限定芒果"); ProductSku s = sku(p, 42, 345, 1); image(s, 9, 0);
        MockMvc mvc = mvc(service(List.of(p)));
        for (String path : List.of("/front/", "/front/index", "/front/index.html", "/front/all-products/", "/front/all-products/index.html", "/front/category/seasonal-fresh-fruit/")) {
            var response = mvc.perform(get(path)).andReturn().getResponse(); assertEquals(200, response.getStatus());
            String html = response.getContentAsString(); assertTrue(html.contains("資料庫限定芒果")); assertTrue(html.contains("NT$ 345")); assertTrue(html.contains("productId=5")); assertTrue(html.contains("data-commerce-sku-id=\"42\"")); assertTrue(html.contains("/product/image/9?v=")); assertFalse(html.contains("?v=null")); assertFalse(html.contains("智利櫻桃禮盒"));
        }
        String deployed = mvc.perform(get("/TKA102G2/front/").contextPath("/TKA102G2")).andReturn().getResponse().getContentAsString(); assertTrue(deployed.contains("/TKA102G2/product/image/9"));
    }
    @Test public void homeCardsExposeActiveSkuChoicesQuantityAndCartButtonWithoutDescription() throws Exception {
        Product p = product(5, "可選規格芒果");
        ProductSku small = sku(p, 42, 345, 1); small.setSkuName("小盒"); small.setStock(3);
        ProductSku large = sku(p, 43, 678, 1); large.setSkuName("大盒"); large.setStock(7); image(large, 99, 0);
        ProductSku offline = sku(p, 44, 999, 0); offline.setSkuName("已下架規格");
        String html = mvc(service(List.of(p))).perform(get("/shop/front/").contextPath("/shop"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("products-purchase-card"));
        assertTrue(html.contains("id=\"home-sku-5\""));
        assertTrue(html.contains("value=\"42\"")); assertTrue(html.contains("value=\"43\""));
        assertTrue(html.contains("data-sku-name=\"大盒\""));
        assertTrue(html.contains("data-price=\"678\"")); assertTrue(html.contains("data-stock=\"10\"")); // 狀態 1：可訂購數量 = min(10, stock)
        assertTrue(html.contains("/shop/product/image/99"));
        assertTrue(html.contains("id=\"home-qty-5\""));
        assertTrue(html.contains("home-card-add")); assertFalse(html.contains("目前缺貨"));
        assertFalse(html.contains("已下架規格")); assertFalse(html.contains("資料庫描述"));
        assertTrue(html.contains("/shop/front/css/home-product-cards.css"));
    }
    @Test public void allProductsHasSingleCollectionAndUsesHomePurchaseCards() throws Exception {
        Product fresh=product(5,"鮮果"),gift=product(8,"芒果禮盒");sku(fresh,42,345,1);sku(gift,43,678,1);
        var catalogMvc=mvc(service(List.of(fresh,gift)));
        var response=catalogMvc.perform(get("/shop/front/all-products/").contextPath("/shop")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
        String html=response.getContentAsString();
        assertTrue(html.contains("全臺精選"));assertTrue(html.contains("home-orange-theme all-products-page"));
        assertTrue(html.contains("id=\"all-products-grid\""));
        assertFalse(html.contains("all-products-giftbox-grid"));assertFalse(html.contains("all-products-fresh-grid"));
        assertTrue(html.contains("data-product-id=\"5\""));assertTrue(html.contains("data-product-id=\"8\""));
        assertTrue(html.contains("home-card-add"));assertTrue(html.contains("home-card-sku"));
        assertTrue(html.contains("/shop/front/css/home-theme.css"));assertTrue(html.contains("/shop/front/js/favorite-store.js"));
        assertEquals(400,catalogMvc.perform(get("/front/api/catalog-order").param("favoriteProductIds","-1"))
            .andReturn().getResponse().getStatus());
    }
    @Test public void rankingPagesAndHomepageShareRankedPurchaseCards() throws Exception {
        Product p=product(5,"暢銷水果");sku(p,42,345,1);var catalogMvc=mvc(service(List.of(p)));
        for(String route:List.of("/front/best-sellers/","/front/top-rated/")) {
            var response=catalogMvc.perform(get(route)).andReturn().getResponse();assertEquals(200,response.getStatus());
            String html=response.getContentAsString();assertTrue(html.contains("products-purchase-card"));
            assertTrue(html.contains("暢銷水果"));assertTrue(html.contains("home-theme.css"));
            assertFalse(html.contains("data-order-url"));assertFalse(html.contains("全部鮮果與禮盒"));
            assertTrue(html.contains(route.contains("best-sellers")?"人氣爆棚":"好評如潮"));
        }
        String home=catalogMvc.perform(get("/front/")).andReturn().getResponse().getContentAsString();
        assertTrue(home.contains("人氣爆棚"));assertTrue(home.contains("好評如潮"));
        assertTrue(home.contains("href=\"/front/best-sellers/\""));assertTrue(home.contains("href=\"/front/top-rated/\""));
        assertFalse(home.contains("熱銷精選"));
    }
    @Test public void stoppingSupplyLabelAppearsOnCardsAndProductDetails() throws Exception {
        Product p=product(23,"停產測試商品");
        ProductSku s=sku(p,102,199,6);s.setAnotherName("最後一批");s.setStock(7);
        var service=service(List.of(p));
        assertEquals("最後一批（即將停產）",service.getProducts().get(0).skus().get(0).name());
        String home=mvc(service).perform(get("/front/")).andReturn().getResponse().getContentAsString();
        assertTrue(home.contains("最後一批（即將停產）"));
        String detail=mvc(service).perform(get("/front/product/view/").param("productId","23")).andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("最後一批（即將停產）"));assertTrue(detail.contains("data-sku-status=\"6\""));
    }
    @Test public void shortageAndRetiringSkusRenderWithQuantityPolicy() throws Exception {
        Product p = product(22, "狀態測試商品");
        ProductSku shortage = sku(p, 100, 100, 2); shortage.setStock(0);shortage.setInboundQty(10);
        ProductSku retiring = sku(p, 101, 200, 3); retiring.setStock(4); retiring.setInboundQty(8); retiring.setOutboundQty(3);
        FrontCatalogService service = service(List.of(p));
        assertEquals(Integer.valueOf(5), service.getProducts().get(0).skus().get(0).stock());
        assertEquals(Integer.valueOf(5), service.getProducts().get(0).skus().get(1).stock());
        String html = mvc(service).perform(get("/front/")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-sku-status=\"2\"")); assertTrue(html.contains("data-sku-status=\"3\""));
        assertTrue(html.contains("（缺貨）")); assertTrue(html.contains("（即將售完）"));
        String detail = mvc(service).perform(get("/front/product/view/").param("productId","22")).andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("data-sku-status=\"2\"")); assertTrue(detail.contains("data-stock=\"5\""));
    }
    @Test public void aliasIsPreferredAndBlankAliasFallsBackAcrossFrontPages() throws Exception {
        Product p = product(22, "測試水果");
        ProductSku named = sku(p, 100, 100, 1); named.setSkuName("原名禮盒"); named.setAnotherName("  特選小盒  ");
        ProductSku blank = sku(p, 101, 200, 1); blank.setSkuName("原名大盒"); blank.setAnotherName(" \t ");
        FrontCatalogService service = service(List.of(p));
        assertEquals("特選小盒", service.getProducts().get(0).skus().get(0).name());
        assertEquals("原名大盒", service.getProducts().get(0).skus().get(1).name());
        assertTrue(service.getProducts().get(0).giftBox());
        for (String route : List.of("/front/", "/front/product/view/")) {
            String html = mvc(service).perform(get(route).param("productId","22")).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("特選小盒")); assertTrue(html.contains("原名大盒")); assertFalse(html.contains(">原名禮盒<"));
        }
        assertEquals("特選小盒", named.getDisplayName()); assertEquals("原名大盒", blank.getDisplayName());
    }
    @Test public void giftCategoryAndCanonicalRoutesRender() throws Exception {
        Product p = product(8, "芒果禮盒"); sku(p, 18, 899, 1);
        MockMvc mvc = mvc(service(List.of(p)));
        for (String path : List.of("/front/", "/front/all-products/", "/front/category/featured-gift-boxes/", "/front/category/featured-gift-boxes/index.html")) {
            var response = mvc.perform(get(path)).andReturn().getResponse(); assertEquals(200, response.getStatus()); assertTrue(response.getContentAsString().contains("芒果禮盒"));
            assertFalse(response.getContentAsString().contains("js/product-catalog.js"));
        }
        for (String path : List.of("/front", "/front/all-products", "/front/category/seasonal-fresh-fruit", "/front/category/featured-gift-boxes")) {
            var response = mvc.perform(get(path)).andReturn().getResponse(); assertEquals(302, response.getStatus()); assertEquals(path + "/", response.getRedirectedUrl());
        }
    }
    @Test public void emptyHomeAndMissingDetailsHaveNoFakeFallback() throws Exception {
        MockMvc mvc = mvc(service(List.of()));
        String home = mvc.perform(get("/front/")).andReturn().getResponse().getContentAsString(); assertTrue(home.contains("目前沒有已上架商品")); assertFalse(home.contains("智利櫻桃"));
        String detail = mvc.perform(get("/front/product/view/").param("productId", "999")).andReturn().getResponse().getContentAsString(); assertTrue(detail.contains("找不到這個商品")); assertFalse(detail.contains("id=\"pv-form\""));
    }
    @Test public void detailUsesIdEvenWhenNamesDuplicate() throws Exception {
        Product a = product(1, "同名水果"); sku(a, 10, 100, 1); Product b = product(2, "同名水果"); sku(b, 20, 200, 1);
        String html = mvc(service(List.of(a,b))).perform(get("/front/product/view/").param("productId", "2")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-commerce-sku-id=\"20\"")); assertTrue(html.contains("NT$ 200")); assertFalse(html.contains("../../js/product-catalog.js"));
    }

    @Test public void mixedSoldOutSkuIsVisibleButCannotBecomeDefaultAndAllSoldOutIsHidden() throws Exception {
        Product p=product(90,"售完顯示測試");sku(p,900,1,4).setSkuName("售完規格");sku(p,901,200,1).setSkuName("可購買規格");sku(p,902,100,0).setSkuName("下架規格");
        Product all=product(91,"全售完商品");sku(all,903,100,4);
        var service=service(List.of(p,all));assertEquals(1,service.getProducts().size());
        assertEquals(Integer.valueOf(901),service.getProducts().get(0).skuId());
        assertEquals(Integer.valueOf(0),service.getProducts().get(0).skus().get(0).stock());
        String html=mvc(service).perform(get("/front/")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("售完規格（售完）"));assertFalse(html.contains("全售完商品"));assertFalse(html.contains("下架規格"));
    }
    private record SearchCategory(Integer id,Integer parent,String name) implements ProductRepository.FrontCategoryRow {
        public Integer getCategoryId(){return id;}public Integer getParentId(){return parent;}public String getName(){return name;}
    }
    private FrontCatalogService.SearchData searchFixture() {
        Product mango=product(1,"台南愛文芒果"),apple=product(2,"Fuji Apple"),low=product(3,"芒果禮盒");
        sku(mango,1,100,1);sku(apple,2,200,1);sku(low,3,300,1);
        return new FrontCatalogService.SearchData(service(List.of(mango,apple,low)).getProducts(),
            List.of(new FrontCatalogService.SearchMeta(1,2,10,"日光果園","台南",4.5),new FrontCatalogService.SearchMeta(2,3,20,"青森果園","日本青森",5),new FrontCatalogService.SearchMeta(3,2,10,"日光果園","台南",3.99)),
            List.of(new SearchCategory(1,null,"芒果"),new SearchCategory(2,1,"愛文芒果"),new SearchCategory(3,null,"蘋果")),
            List.of(new FrontCatalogService.SearchPromotion(1,7,"夏日芒果優惠")));
    }
    @Test public void productSearchCombinesTextCategoryOriginSupplierPromotionAndRating() {
        var result=FrontCatalogService.search(searchFixture(),new FrontCatalogService.SearchQuery("臺南 芒果",1,"台南",10,"7",true),0);
        assertEquals(1,result.total());assertEquals(Integer.valueOf(1),result.products().get(0).productId());assertEquals(6,result.conditions().size());
        assertEquals(0,FrontCatalogService.search(searchFixture(),new FrontCatalogService.SearchQuery("芒果",1,"日本青森",10,"7",true),0).total());
    }
    @Test public void productSearchIsCaseInsensitiveLiteralAndUsesUnroundedRating() {
        assertEquals(1,FrontCatalogService.search(searchFixture(),new FrontCatalogService.SearchQuery("ＦＵＪＩ",null,"",null,"",false),0).total());
        assertEquals(0,FrontCatalogService.search(searchFixture(),new FrontCatalogService.SearchQuery("%_",null,"",null,"",false),0).total());
        var result=FrontCatalogService.search(searchFixture(),new FrontCatalogService.SearchQuery("芒果",null,"",null,"",true),0);assertEquals(1,result.total());
        assertEquals(1,FrontCatalogService.search(searchFixture(),new FrontCatalogService.SearchQuery("",null,"",null,"any",false),0).total());
    }
    @Test public void productSearchPaginatesStablyAndHandlesExpiredFilters() {
        var data=searchFixture();var products=new java.util.ArrayList<FrontCatalogService.ProductView>();
        for(int i=1;i<=45;i++)products.add(new FrontCatalogService.ProductView(i,"果物 "+i,"",100,null,i,null,false,List.of(),1,0.0));
        var many=new FrontCatalogService.SearchData(products,List.of(),List.of(),List.of());
        assertEquals(20,FrontCatalogService.search(many,FrontCatalogService.SearchQuery.empty(),0).products().size());
        var second=FrontCatalogService.search(many,FrontCatalogService.SearchQuery.empty(),1);assertEquals(Integer.valueOf(21),second.products().get(0).productId());assertEquals(3,second.pages());
        assertEquals(5,FrontCatalogService.search(many,FrontCatalogService.SearchQuery.empty(),999).products().size());
        assertEquals(0,FrontCatalogService.search(data,new FrontCatalogService.SearchQuery("",999,"",null,"",false),0).total());
    }
    @Test public void searchDialogAndResultsRenderRememberedQuerySafely() throws Exception {
        var data=searchFixture();var catalog=new FrontCatalogService(){@Override public SearchData searchData(){return data;}};
        var mvc=mvc(catalog);var session=new org.springframework.mock.web.MockHttpSession();
        var result=mvc.perform(get("/front/search/").param("q","芒果").param("category","1").param("origin","台南").param("vendor","10").param("promotion","7").param("highRated","true").session(session)).andReturn().getResponse();
        assertEquals(200,result.getStatus());assertTrue(result.getContentAsString().contains("找到 1 個商品"));assertTrue(result.getContentAsString().contains("台南愛文芒果"));
        var saved=(FrontCatalogService.SearchQuery)session.getAttribute("product.lastSearch");assertEquals("芒果",saved.q());assertTrue(saved.highRated());
        var popup=mvc.perform(get("/front/search-dialog/").session(session)).andReturn().getResponse();assertEquals(200,popup.getStatus());assertTrue(popup.getContentAsString().contains("value=\"芒果\""));assertTrue(popup.getContentAsString().contains("fruit-search-orange")); assertTrue(popup.getContentAsString().contains("data-category-parent=" + (char)34 + "1" + (char)34)); assertFalse(popup.getContentAsString().contains("開始搜尋後，會記住這次的搜尋條件。"));
        assertTrue(popup.getContentAsString().contains("maxlength="+(char)34+"20"+(char)34));
        assertEquals(200,mvc.perform(get("/front/search/").param("q","果".repeat(20))).andReturn().getResponse().getStatus());
        var escaped=mvc.perform(get("/front/search/").param("q","<script>x</script>")).andReturn().getResponse().getContentAsString();assertFalse(escaped.contains("<script>x</script>"));assertTrue(escaped.contains("&lt;script&gt;")); assertFalse(escaped.contains("目前沒有已上架商品。"));
        assertEquals(400,mvc.perform(get("/front/search/").param("promotion","-1")).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/front/search/").param("q","x".repeat(21))).andReturn().getResponse().getStatus());
    }
    @Test public void searchOriginsHideTaiwanPrefixAndOrderWestThenEastThenIslands() {
        var fixture=searchFixture();
        var origins=List.of("日本青森","台灣金門金城","臺灣花蓮壽豐","台灣屏東佳冬","台灣台北市","臺灣宜蘭三星","台灣桃園拉拉山",
            "台灣台南玉井","台灣澎湖馬公","臺灣臺東卑南","台灣台中東勢","臺灣連江南竿","台灣基隆市","台灣新北市","台灣新竹縣","台灣苗栗縣",
            "臺灣彰化溪州","台灣南投埔里","台灣雲林古坑","台灣嘉義阿里山","臺灣高雄燕巢");
        var metadata=new java.util.ArrayList<FrontCatalogService.SearchMeta>();
        for(int i=0;i<origins.size();i++)metadata.add(new FrontCatalogService.SearchMeta(i+1,2,10,"果園",origins.get(i),4.5));
        var data=new FrontCatalogService.SearchData(fixture.products(),metadata,fixture.categories(),List.of());
        var result=FrontCatalogService.search(data,FrontCatalogService.SearchQuery.empty(),0);
        assertEquals(List.of("基隆市","台北市","新北市","桃園拉拉山","新竹縣","苗栗縣","台中東勢","彰化溪州","南投埔里","雲林古坑","嘉義阿里山","台南玉井","高雄燕巢","屏東佳冬",
            "宜蘭三星","花蓮壽豐","臺東卑南","澎湖馬公","金門金城","連江南竿","日本青森"),result.options().origins().stream().map(FrontCatalogService.SearchOption::label).toList());
        assertTrue(result.options().origins().stream().anyMatch(o->o.value().equals("臺灣臺東卑南")&&o.label().equals("臺東卑南")));
        var selected=FrontCatalogService.search(data,new FrontCatalogService.SearchQuery("",null,"台灣金門金城",null,"",false),0);
        assertEquals(1,selected.total());assertEquals(List.of("產地：金門金城"),selected.conditions());
    }
}
