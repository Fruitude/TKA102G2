package com.fruitude.product.controller;

import java.util.List;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.fruitude.product.model.FrontCatalogService;
import com.fruitude.product.model.FrontCatalogService.ProductView;
import com.fruitude.orders.model.OrdersService;
import com.fruitude.promo.model.PromoService;
import com.fruitude.farmer.model.FarmerService;
import com.fruitude.farmer.model.FarmerVO;
import com.fruitude.vendor.model.VendorVO;
import jakarta.servlet.http.HttpSession;

@Controller
public class FrontProductController {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.fruitude.product.model.ProductImageService imageCache;
    @org.springframework.web.bind.annotation.ModelAttribute("productImageVersions")
    public java.util.Map<Integer,String> productImageVersions() {
        return imageCache==null ? java.util.Map.of() : imageCache.browserVersions();
    }

    // 小農資訊頁的一位小農：資料來自 farmer 與 vendor（後台資訊管理勾選「顯示在小農資訊」），故事依換行拆成段落
    public record FarmerView(Integer vendorId, String name, String origin, String growProduct, String imagePath,
        String slogan, List<String> paragraphs, List<ProductView> products) {}

    private final FrontCatalogService catalog;
    private final PromoService promoService;
    private final OrdersService ordersService;
    @org.springframework.beans.factory.annotation.Autowired
    private FarmerService farmerService;
    public FrontProductController(FrontCatalogService catalog, PromoService promoService, OrdersService ordersService) {
        this.catalog = catalog;
        this.promoService = promoService;
        this.ordersService = ordersService;
    }

    // Favorites store product IDs, while numeric image IDs may change after an import.
    // Resolve on each request and let the existing image endpoint handle bytes and caching.
    @GetMapping("/front/api/product-image/{productId}")
    public ResponseEntity<Void> productImage(@PathVariable int productId,
            jakarta.servlet.http.HttpServletRequest request) {
        if (productId <= 0) return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        Integer imageId = imageCache == null ? null : imageCache.findProductImageId(productId);
        if (imageId == null) return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore())
            .location(java.net.URI.create(request.getContextPath() + "/product/image/" + imageId
                + "?v=" + imageCache.imageVersion(imageId))).build();
    }

    @GetMapping("/front/search-dialog/")
    public String searchDialog(HttpSession session,Model model,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        var query=session.getAttribute("product.lastSearch") instanceof FrontCatalogService.SearchQuery saved?saved:FrontCatalogService.SearchQuery.empty();
        var result=FrontCatalogService.search(catalog.searchData(),query,0);
        model.addAttribute("searchQuery",query);model.addAttribute("searchOptions",result.options());
        return "front/search/dialog";
    }
    @GetMapping("/front/search")
    public String canonicalSearch(jakarta.servlet.http.HttpServletRequest request) {
        String query=request.getQueryString();return "redirect:/front/search/"+(query==null?"":"?"+query);
    }
    @GetMapping("/front/search/")
    public String searchProducts(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Integer category,
            @RequestParam(defaultValue="") String origin,@RequestParam(required=false) Integer vendor,
            @RequestParam(defaultValue="") String promotion,@RequestParam(defaultValue="false") boolean highRated,
            @RequestParam(defaultValue="0") int page,HttpSession session,Model model,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try {
            var query=new FrontCatalogService.SearchQuery(q,category,origin,vendor,promotion,highRated);
            var result=FrontCatalogService.search(catalog.searchData(),query,page);
            session.setAttribute("product.lastSearch",query);model.addAttribute("searchQuery",query);model.addAttribute("searchResult",result);
            model.addAttribute("searchProducts",result.products());return "front/search/index";
        }catch(IllegalArgumentException invalid){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage());}
    }


    @GetMapping("/front")
    public String canonicalHome() { return "redirect:/front/"; }

    @GetMapping({"/front/", "/front/index", "/front/index.html"})
    public String homePage(Model model, HttpSession session, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("homeFeedback", catalog.getHomeFeedback());
        model.addAttribute("bestSellerProducts", catalog.getBestSellers().stream().limit(20).toList());
        model.addAttribute("topRatedProducts", catalog.getTopRatedProducts().stream().limit(20).toList());
        // 首頁活動區塊：限時活動與會員專屬福利。會員身分只信任 session，用來標示目前符合資格的福利（壽星月、新會員首購）
        Integer memberId = session.getAttribute("loggedInMemberId") instanceof Integer id ? id : null;
        model.addAttribute("homeAllProducts", catalog.getAllProducts(memberId, List.of()).stream().limit(20).toList());
        var promos = promoService.findHomePromos(memberId == null
                ? com.fruitude.promo.model.MemberPromoState.ANONYMOUS : ordersService.memberPromoState(memberId));
        model.addAttribute("limitedPromos", promos.limited());
        model.addAttribute("memberPerks", promos.perks());
        return "front/index";
    }

    public String home(Model model) {
        model.addAttribute("homeFeedback", catalog.getHomeFeedback());
        List<ProductView> products = catalog.getProducts();
        model.addAttribute("freshProducts", products.stream().filter(p -> !p.giftBox()).toList());
        model.addAttribute("giftProducts", products.stream().filter(ProductView::giftBox).toList());
        return "front/index";
    }

    @GetMapping({"/front/best-sellers/", "/front/best-sellers/index", "/front/best-sellers/index.html", "/front/best-sellers.html"})
    public String bestSellers(Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("rankedProducts", catalog.getBestSellers());
        return "front/best-sellers";
    }

    @GetMapping({"/front/top-rated/", "/front/top-rated/index", "/front/top-rated/index.html", "/front/top-rated.html"})
    public String topRated(Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("rankedProducts", catalog.getTopRatedProducts());
        return "front/top-rated";
    }

    @GetMapping("/front/best-sellers")
    public String canonicalBestSellers() { return "redirect:/front/best-sellers/"; }

    @GetMapping("/front/top-rated")
    public String canonicalTopRated() { return "redirect:/front/top-rated/"; }

    @GetMapping({"/front/all-products/", "/front/all-products/index", "/front/all-products/index.html"})
    public String allProducts(Model model, HttpSession session, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        Integer memberId = session.getAttribute("loggedInMemberId") instanceof Integer id ? id : null;
        var products = catalog.getAllProducts(memberId, List.of());
        String token = java.util.UUID.randomUUID().toString();
        synchronized (session) {
            var snapshots = catalogSnapshots(session);
            snapshots.put(token, new CatalogSnapshot(List.copyOf(products), memberId, System.currentTimeMillis(), false));
            while (snapshots.size() > 4) snapshots.remove(snapshots.keySet().iterator().next());
        }
        model.addAttribute("allProducts", products.subList(0, Math.min(20, products.size())));
        model.addAttribute("catalogToken", token);
        model.addAttribute("catalogTotal", products.size());
        model.addAttribute("catalogOffset", Math.min(20, products.size()));
        return "front/all-products/index";
    }

    private record CatalogSnapshot(List<ProductView> products, Integer memberId, long created, boolean personalized) {}

    @SuppressWarnings("unchecked")
    private java.util.LinkedHashMap<String, CatalogSnapshot> catalogSnapshots(HttpSession session) {
        var snapshots = (java.util.LinkedHashMap<String, CatalogSnapshot>) session.getAttribute("product.catalogSnapshots");
        if (snapshots == null) {
            snapshots = new java.util.LinkedHashMap<>();
            session.setAttribute("product.catalogSnapshots", snapshots);
        }
        snapshots.values().removeIf(snapshot -> System.currentTimeMillis() - snapshot.created() > 15 * 60_000L);
        return snapshots;
    }

    @GetMapping("/front/api/catalog-batch")
    public String catalogBatch(@RequestParam String token, @RequestParam int offset,
            @RequestParam(required=false) List<Integer> favoriteProductIds, HttpSession session,
            Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (offset < 0 || (favoriteProductIds != null && (favoriteProductIds.size() > 500
                || favoriteProductIds.stream().anyMatch(id -> id == null || id <= 0))))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        Integer memberId = session.getAttribute("loggedInMemberId") instanceof Integer id ? id : null;
        List<ProductView> products;
        synchronized (session) {
            var snapshots = catalogSnapshots(session);
            var snapshot = snapshots.get(token);
            if (snapshot == null || !java.util.Objects.equals(memberId, snapshot.memberId()))
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE);
            products = snapshot.products();
            // Personalize once, before fetching later batches; never reshuffle on scrolling or favorite toggles.
            if (offset == 0 && !snapshot.personalized() && memberId != null
                    && favoriteProductIds != null && !favoriteProductIds.isEmpty()) {
                products = catalog.rankExistingProducts(products, memberId, favoriteProductIds);
                snapshots.put(token, new CatalogSnapshot(List.copyOf(products), memberId, snapshot.created(), true));
            }
        }
        if (offset > products.size()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        int end = Math.min(products.size(), offset + 20);
        model.addAttribute("products", products.subList(offset, end));
        response.setHeader("X-Catalog-Next", String.valueOf(end));
        response.setHeader("X-Catalog-Total", String.valueOf(products.size()));
        return "front/fragments/product-grid :: purchaseCards";
    }

    @ResponseBody
    @GetMapping("/front/api/catalog-order")
    public ResponseEntity<List<Integer>> catalogOrder(
            @RequestParam(required=false) List<Integer> favoriteProductIds, HttpSession session) {
        if (favoriteProductIds != null && (favoriteProductIds.size() > 500
                || favoriteProductIds.stream().anyMatch(id -> id == null || id <= 0)))
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        Integer memberId = session.getAttribute("loggedInMemberId") instanceof Integer id ? id : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog
            .getAllProducts(memberId, memberId == null || favoriteProductIds == null ? List.of() : favoriteProductIds)
            .stream().map(ProductView::productId).toList());
    }

    @GetMapping("/front/all-products")
    public String canonicalProducts() { return "redirect:/front/all-products/"; }

    @GetMapping({"/front/category/seasonal-fresh-fruit/", "/front/category/seasonal-fresh-fruit/index", "/front/category/seasonal-fresh-fruit/index.html"})
    public String fresh(Model model) {
        home(model);
        return "front/category/seasonal-fresh-fruit/index";
    }

    @GetMapping({"/front/category/featured-gift-boxes/", "/front/category/featured-gift-boxes/index", "/front/category/featured-gift-boxes/index.html"})
    public String gifts(Model model) {
        home(model);
        return "front/category/featured-gift-boxes/index";
    }

    @GetMapping("/front/category/seasonal-fresh-fruit")
    public String canonicalFresh() { return "redirect:/front/category/seasonal-fresh-fruit/"; }

    @GetMapping("/front/category/featured-gift-boxes")
    public String canonicalGifts() { return "redirect:/front/category/featured-gift-boxes/"; }
    @GetMapping({"/front/product/view/", "/front/product/view/index", "/front/product/view/index.html"})
    public String detail(@RequestParam(required = false) Integer productId, @RequestParam(required = false) String name, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        List<ProductView> products = catalog.getProducts();
        Integer id = productId != null ? productId : products.stream().filter(p -> name != null && name.equals(p.name())).map(ProductView::productId).findFirst().orElse(null);
        ProductView selected = catalog.getLiveProduct(id);
        model.addAttribute("dbProduct", selected);
        model.addAttribute("productSupplier", selected == null ? null : catalog.getProductSupplier(selected.productId()));
        model.addAttribute("reviewSkus",selected==null ? List.of() : selected.skus().stream()
            .filter(sku->java.util.Set.of(1,2,3,4,6).contains(sku.skuStatus())).toList());
        model.addAttribute("productReviews", selected==null || catalog==null
            ? com.fruitude.product.model.FrontCatalogService.ReviewPage.empty() : catalog.read(selected.productId(),0,3));
        model.addAttribute("relatedProducts", com.fruitude.product.model.FrontCatalogService.recommend(selected,products,60));
        return "front/product/view/index";
    }

    @GetMapping("/front/api/cart-products")
    @ResponseBody
    public org.springframework.http.ResponseEntity<List<FrontCatalogService.LiveSku>> cartProducts(@RequestParam List<Integer> skuIds) {
        if (skuIds.size() > 500 || skuIds.stream().anyMatch(id -> id == null || id <= 0))
            return org.springframework.http.ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore()).build();
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(catalog.getLiveSkus(skuIds));
    }

    @ModelAttribute("productReviewReportToken")
    public String productReviewReportToken(jakarta.servlet.http.HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null||!(session.getAttribute("loggedInMemberId") instanceof Integer))return "";
        synchronized(session){var token=session.getAttribute("product.reviewReportToken");
            if(!(token instanceof String)){token=java.util.UUID.randomUUID().toString();session.setAttribute("product.reviewReportToken",token);}
            return (String)token;}
    }
    public record ReviewReportRequest(Integer productId,String reason,String note) {}
    @ResponseBody
    @PostMapping(value="/front/api/product-reviews/{reviewId}/report",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reportReview(@PathVariable int reviewId,@RequestBody ReviewReportRequest form,
            @RequestHeader(value="X-Product-Review-Token",required=false) String token,HttpSession session,jakarta.servlet.http.HttpServletRequest request) {
        if(!(session.getAttribute("loggedInMemberId") instanceof Integer memberId))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("message","請先登入會員後再檢舉"));
        if(token==null||!token.equals(session.getAttribute("product.reviewReportToken")))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("message","請重新整理頁面後再試"));
        if(form==null||form.productId()==null||form.productId()<=0||reviewId<=0)
            return ResponseEntity.badRequest().body(java.util.Map.of("message","檢舉資料不正確"));
        try {
            boolean changed=catalog.reportReview(form.productId(),reviewId,memberId,form.reason(),form.note(),request.getRemoteAddr());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("reported",changed,
                "message",changed?"檢舉已送出，評論已暫時隱藏，評分與評論數保留。":"此評論已被檢舉或不再公開。"));
        }catch(SecurityException invalid){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("message",invalid.getMessage()));}
        catch(IllegalArgumentException invalid){return ResponseEntity.badRequest().body(java.util.Map.of("message",invalid.getMessage()));}
    }

    // ProductController endpoints



    @ResponseBody
    @GetMapping("/front/api/product-reviews")
    public ResponseEntity<?> read(@RequestParam int productId,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="latest") String sort,
        @RequestParam(required=false) Integer skuId,@RequestParam(defaultValue="20") int size) {
        if(productId<=0 || page<0 || page>100000)return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.read(productId,page,size,keyword,sort,skuId));
        } catch(IllegalArgumentException ex) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(java.util.Map.of("message",ex.getMessage()));
        }
    }

    // 小農資訊頁：每位小農各自販售的商品，讓會員看完文章後可以直接加入購物車
    @GetMapping({"/front/farmers/", "/front/farmers/index", "/front/farmers/index.html"})
    public String farmers(Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        List<FarmerVO> farmerRows = farmerService.getFarmersForFront();
        List<Integer> vendorIds = new java.util.ArrayList<>();
        for (FarmerVO farmer : farmerRows) vendorIds.add(farmer.getVendorId());
        java.util.Map<Integer, List<ProductView>> productsByVendor = catalog.getProductsByVendorIds(vendorIds);
        List<FarmerView> farmers = new java.util.ArrayList<>();
        for (FarmerVO farmer : farmerRows) {
            VendorVO vendor = farmer.getVendor();
            List<String> paragraphs = new java.util.ArrayList<>();
            if (farmer.getFarmerStory() != null) {
                for (String line : farmer.getFarmerStory().split("\\R")) {
                    if (!line.isBlank()) paragraphs.add(line.trim());
                }
            }
            // 沒有填小農故事時，退而顯示供應商介紹
            if (paragraphs.isEmpty() && vendor.getVendorIntroduction() != null) paragraphs.add(vendor.getVendorIntroduction());
            farmers.add(new FarmerView(vendor.getVendorId(), vendor.getContactPerson(), vendor.getOrigin(), farmer.getGrowProduct(),
                farmer.getFarmerImagePath(), farmer.getFarmerSlogan(), paragraphs, productsByVendor.get(vendor.getVendorId())));
        }
        model.addAttribute("farmers", farmers);
        return "front/farmers/index";
    }

    @GetMapping("/front/farmers")
    public String canonicalFarmers() { return "redirect:/front/farmers/"; }

}
