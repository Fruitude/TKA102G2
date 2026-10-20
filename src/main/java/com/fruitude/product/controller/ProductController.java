package com.fruitude.product.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import com.fruitude.product.model.Product;
import com.fruitude.product.model.ProductCategory;
import com.fruitude.product.model.ProductCategoryService;
import com.fruitude.product.model.ProductImage;
import com.fruitude.product.model.ProductService;
import com.fruitude.product.model.ProductSku;
import com.fruitude.vendor.model.VendorVO;
import com.fruitude.vendor.model.VendorService;
import jakarta.validation.Valid;
import com.fruitude.product.model.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;

@Controller
public class ProductController {
    public ProductController() {}
    public ProductController(ProductService service) { this.productSvc=service; }
    @Autowired
    private com.fruitude.product.model.ProductPageStatusService productPageStatusSvc;


    @org.springframework.web.bind.annotation.InitBinder("product")
    void bind(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("productId", "productName", "productDesc", "status", "vendor.vendorId", "productCategory.productCategoryId",
            "productSkus[*].skuName", "productSkus[*].anotherName", "productSkus[*].price", "productSkus[*].stock", "productSkus[*].safetyStock", "productSkus[*].inboundQty", "productSkus[*].outboundQty",
            "productSkus[*].maxBackorderQty", "productSkus[*].purchaseAddOnQty", "productSkus[*].status", "productSkus[*].defaultListingTime");
    }

    @ModelAttribute("earliestSkuListingTime")
    public String earliestSkuListingTime() {
        return ProductService.earliestListingTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    @ModelAttribute("latestSkuListingTime")
    public String latestSkuListingTime() {
        return ProductService.latestListingTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private void validate(Product p, BindingResult result) {
        if (p.getProductName() == null || p.getProductName().trim().isEmpty())
            result.rejectValue("productName", "required", "商品名稱不能空白");
        else if (p.getProductName().trim().length() > 100)
            result.rejectValue("productName", "invalid", "商品名稱須為 1～100 字");
        else p.setProductName(p.getProductName().trim());
        if (p.getProductDesc() != null && p.getProductDesc().length() > 255) result.rejectValue("productDesc", "invalid", "描述不可超過 255 字");
        if (p.getStatus() == null || p.getStatus() < 0 || p.getStatus() > 2) result.rejectValue("status", "invalid", "狀態須為 0～2");
        Integer categoryId = p.getProductCategory() == null ? null : p.getProductCategory().getProductCategoryId();
        Integer vendorId = p.getVendor() == null ? null : p.getVendor().getVendorId();
        ProductCategory category = categoryId == null ? null : productCategorySvc.getOneProductCategory(categoryId);
        VendorVO vendor = vendorId == null ? null : vendorSvc.getOneVendor(vendorId);
        if (category == null) result.reject("category", "請選擇有效分類"); else p.setProductCategory(category);
        if (vendor == null) result.reject("vendor", "請選擇有效廠商"); else p.setVendor(vendor);
        if (vendor != null && p.getProductName() != null && productSvc.isNameUsed(vendorId, p.getProductName(), p.getProductId()))
            result.rejectValue("productName", "duplicate", "該供應商已有同名的商品名稱");
    }

    @Autowired
    ProductService productSvc;

    @Autowired
    ProductCategoryService productCategorySvc;

    @Autowired
    VendorService vendorSvc;

    /*
     * 顯示新增商品頁面
     */
    @org.springframework.web.bind.annotation.ResponseBody
    @GetMapping("/product/name-availability")
    public org.springframework.http.ResponseEntity<java.util.Map<String,Boolean>> productNameAvailability(
            @RequestParam Integer vendorId, @RequestParam(defaultValue="") String name,
            @RequestParam(required=false) Integer productId) {
        name=name.trim();
        if(vendorId<=0 || name.isEmpty() || name.length()>100 || (productId!=null && productId<=0))
            return org.springframework.http.ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore()).build();
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
            .body(java.util.Map.of("exists",productSvc.isNameUsed(vendorId,name,productId)));
    }

    private record NextSkuDraft(String name,String alias) implements java.io.Serializable {}
    private record NextProductDraft(Integer vendorId,Integer categoryId,String name,String description,
            List<NextSkuDraft> skus) implements java.io.Serializable {}

    @GetMapping("/product/addProduct")
    public String addProduct(ModelMap model) {
        Product product = new Product();
        if(model.get("nextProductDraft") instanceof NextProductDraft draft) {
            if(draft.vendorId()!=null) product.setVendor(vendorSvc.getOneVendor(draft.vendorId()));
            if(draft.categoryId()!=null) product.setProductCategory(productCategorySvc.getOneProductCategory(draft.categoryId()));
            product.setProductName(draft.name());product.setProductDesc(draft.description());
            for(var value:draft.skus()) { var sku=new ProductSku();sku.setSkuName(value.name());sku.setAnotherName(value.alias());product.getProductSkus().add(sku); }
        }
        if(product.getProductSkus().isEmpty()) product.getProductSkus().add(new ProductSku());
        model.addAttribute("product", product);
        
        List<ProductCategory> categoryList =
                productCategorySvc.getAll();
        
        List<ProductCategory> parentCategoryList =
                categoryList.stream()
                        .filter(category ->
                                category.getParentCategory() == null)
                        .collect(Collectors.toList());
        
        model.addAttribute(
                "parentCategoryListData",
                parentCategoryList
        );
        
        model.addAttribute(
                "productCategoryListData",
                categoryList
        );
        
        List<VendorVO> vendorList =
                vendorSvc.getAll();

        model.addAttribute(
                "vendorListData",
                vendorList
        );


        return "admin/productmanagement/product/addProduct";
    }

    /*
     * 新增商品
     */
    @PostMapping("/product/insert")
    public String insert(
            @Valid @ModelAttribute("product") Product product,
            BindingResult result,
            @RequestParam(defaultValue = "false") boolean activateSkus,
            MultipartHttpServletRequest multipartRequest,
            ModelMap model,
            @RequestParam(defaultValue="false") boolean createNext,
            @RequestParam(required=false) List<String> carryFields,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {

        product.setProductId(null);
        validate(product, result);
        java.util.Set<String> names = new java.util.HashSet<>();
        if (product.getProductSkus().isEmpty()) result.reject("sku", "至少新增一個規格");
        var earliestListing=ProductService.earliestListingTime();
        for (int skuIndex=0;skuIndex<product.getProductSkus().size();skuIndex++) {
            ProductSku sku=product.getProductSkus().get(skuIndex);
            String listingError=ProductService.listingTimeError(sku,earliestListing);
            if(listingError!=null)result.rejectValue("productSkus["+skuIndex+"].defaultListingTime","listingTime",listingError);
            if(!Byte.valueOf((byte)5).equals(sku.getStatus()))sku.setDefaultListingTime(null);
            if(Byte.valueOf((byte)2).equals(product.getStatus()) && Byte.valueOf((byte)5).equals(sku.getStatus()))
                result.reject("sku","永久停產商品不可設定預備上架");
            String error = ProductSkuController.validateProductSku(sku);
            if (error != null) result.reject("sku", error);
            if(sku.getPrice()!=null && sku.getPrice()>ProductService.MAX_PRICE) result.reject("sku", "規格價格不可超過10,000,000元");
            if(sku.getMaxBackorderQty()==null || sku.getMaxBackorderQty()<0) result.reject("sku", "允許超賣量須為0或正整數");
            if(sku.getPurchaseAddOnQty()==null || sku.getPurchaseAddOnQty()<0) result.reject("sku", "採購溢額量須為0或正整數");
            if (sku.getSkuName() != null && !names.add(sku.getSkuName().trim().toLowerCase(java.util.Locale.ROOT))) result.reject("sku", "規格名稱不可重複");
        }

        // 輸入格式驗證失敗，回到新增頁面
        if (result.hasErrors()) {
        	List<ProductCategory> categoryList =
                    productCategorySvc.getAll();

            List<ProductCategory> parentCategoryList =
                    categoryList.stream()
                            .filter(category ->
                                    category.getParentCategory() == null)
                            .collect(Collectors.toList());

            model.addAttribute(
                    "parentCategoryListData",
                    parentCategoryList
            );

            model.addAttribute(
                    "productCategoryListData",
                    categoryList
            );


            // 重新準備供應商資料
            List<VendorVO> vendorList =
                    vendorSvc.getAll();

            model.addAttribute(
                    "vendorListData",
                    vendorList
            );
        	
            return "admin/productmanagement/product/addProduct";
        }
        
     // =========================
        // 1. 商品規格
        // =========================

        List<ProductSku> productSkus = product.getProductSkus();

        if (productSkus != null) {

        	for (int i = 0; i < productSkus.size(); i++) {

        	    ProductSku sku = productSkus.get(i);
                sku.setSkuId(null);
                sku.setSkuName(sku.getSkuName().trim());

        	    // SKU → Product
        	    sku.setProduct(product);

        	    // =========================
        	    // SKU 預設值
        	    // =========================

        	    if (sku.getStock() == null) {
        	        sku.setStock(0);
        	    }

        	    if (sku.getSafetyStock() == null) {
        	        sku.setSafetyStock(0);
        	    }

        	    if (sku.getInboundQty() == null) {
        	        sku.setInboundQty(0);
        	    }

        	    if (sku.getOutboundQty() == null) {
        	        sku.setOutboundQty(0);
        	    }

        	    if (sku.getAllCommentAmount() == null) {
        	        sku.setAllCommentAmount(0);
        	    }

        	    if (sku.getAllCommentStar() == null) {
        	        sku.setAllCommentStar(0);
        	    }

        	    if (sku.getCreatedAt() == null) {
        	        sku.setCreatedAt(LocalDateTime.now());
        	    }


        	    // =========================
        	    // SKU 圖片
        	    // =========================

                int imageOrder=0;
                for(MultipartFile file:multipartRequest.getFiles("skuImages["+i+"]")) {
                    if(file.isEmpty() && (file.getOriginalFilename()==null || file.getOriginalFilename().isBlank()))continue;
                    try {
                        ProductImage image=new ProductImage();image.setProductSku(sku);
                        com.fruitude.product.model.ProductImageService.ImageUploadSupport.apply(image,file);
                        image.setSortOrder(imageOrder++);sku.getProductImages().add(image);
                    } catch(IOException | IllegalArgumentException error) {
                        model.addAttribute("errorMessage","規格「"+sku.getSkuName()+"」的圖片「"+uploadName(file)+"」："
                            +(error instanceof IOException ? "圖片內容無法讀取" : error.getMessage())+"。商品尚未新增，請重新選擇圖片。");
                        return "admin/productmanagement/product/addProduct";
                    }
                }
            }
        }

        // 新增資料
        try { productSvc.addProduct(product, activateSkus); }
        catch (IllegalArgumentException | IllegalStateException e) {
            result.reject("status", e.getMessage()); return "admin/productmanagement/product/addProduct";
        }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            result.reject("save", "商品或規格名稱重複，或關聯資料已變更");
            return "admin/productmanagement/product/addProduct";
        }

        if(createNext) {
            var fields=carryFields==null?java.util.Set.<String>of():new java.util.HashSet<>(carryFields);
            var skus=(fields.contains("skuName")||fields.contains("anotherName"))
                ? product.getProductSkus().stream().map(sku->new NextSkuDraft(fields.contains("skuName")?sku.getSkuName():null,
                    fields.contains("anotherName")?sku.getAnotherName():null)).toList():List.<NextSkuDraft>of();
            redirect.addFlashAttribute("nextProductDraft",new NextProductDraft(
                fields.contains("vendor")?product.getVendor().getVendorId():null,
                fields.contains("category")?product.getProductCategory().getProductCategoryId():null,
                fields.contains("name")?product.getProductName():null,fields.contains("description")?product.getProductDesc():null,skus));
            redirect.addFlashAttribute("success","商品新增成功，可繼續新增下一個。");
            return "redirect:/product/addProduct";
        }

        // 新增完成，準備轉交
        redirect.addFlashAttribute("success", "- (新增成功)");

        return "redirect:/product/listAllProduct";
    }

    /*
     * 查詢單筆商品，準備修改
     */
    @PostMapping("/product/getOne_For_Update")
    public String getOne_For_Update(
            @RequestParam("productId") Integer productId,
            ModelMap model) {

        Product product =
                productSvc.getOneProduct(productId);

        if (product == null) return "redirect:/product/listAllProduct";
        model.addAttribute("product", product);

        return "admin/productmanagement/product/update_product_input";
    }

    /*
     * 修改商品
     */
    @PostMapping("/product/update")
    public String update(
            @Valid @ModelAttribute("product") Product product,
            BindingResult result,
            @RequestParam(defaultValue = "false") boolean activateSkus,
            ModelMap model) {

        if (product.getProductId() == null || productSvc.getOneProduct(product.getProductId()) == null) return "redirect:/product/listAllProduct";
        validate(product, result);

        // 輸入格式驗證失敗，回到修改頁面
        if (result.hasErrors()) {
            return "admin/productmanagement/product/update_product_input";
        }

        // 修改資料
        try { productSvc.updateBasicFields(product, activateSkus); }
        catch (IllegalArgumentException | IllegalStateException e) {
            result.reject("status", e.getMessage());
            return "admin/productmanagement/product/update_product_input";
        }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            result.reject("save", "商品名稱重複，或關聯資料已變更");
            return "admin/productmanagement/product/update_product_input";
        }

        // 修改完成，重新查詢最新資料
        product = productSvc.getOneProduct(product.getProductId());

        model.addAttribute("product", product);
        model.addAttribute("success", "- (修改成功)");
        model.addAttribute("productSkuList", product.getProductSkus());
        model.addAttribute("productImageList", product.getProductSkus().stream().flatMap(s -> s.getProductImages().stream()).filter(i -> i.getImageData() != null).toList());

        return "admin/productmanagement/product/listOneProduct";
    }
    
    @PostMapping("/product/updateStatus")
    @ResponseBody
    public org.springframework.http.ResponseEntity<String> updateStatus(
            @RequestParam Integer productId, @RequestParam Byte status,
            @RequestParam(defaultValue = "false") boolean activateSkus) {
        try {
            if (!productSvc.updateStatus(productId, status, activateSkus))
                return org.springframework.http.ResponseEntity.status(404).body("商品不存在");
            var actual = productSvc.getOneProduct(productId);
            return org.springframework.http.ResponseEntity.ok()
                .header("X-Product-Status", String.valueOf(actual.getStatus()))
                .header("X-Sku-Statuses", actual.getProductSkus().stream()
                    .map(sku -> "\"" + sku.getSkuId() + "\":" + sku.getStatus())
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"))).body("success");
        } catch (com.fruitude.product.model.ProductLifecycleService.ProductStatusConfirmationException e) {
            return org.springframework.http.ResponseEntity.status(409).body(e.getMessage());
        } catch (com.fruitude.product.model.ProductStatusAccess.ProductStatusAccessException e) {
            return org.springframework.http.ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/product/updatePageStatus")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> updatePageStatus(
            @RequestParam List<Integer> productIds, @RequestParam Byte status,
            @RequestParam(defaultValue = "false") boolean activateSkus) {
        try {
            return org.springframework.http.ResponseEntity.ok(productPageStatusSvc.updatePageStatus(productIds,status,activateSkus));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    /*
     * 刪除商品
     */
    @PostMapping("/product/delete")
    public String delete(
            @RequestParam("productId") Integer productId,
            @RequestParam(defaultValue = "") String returnQuery,
            org.springframework.web.servlet.mvc.support.RedirectAttributes model) {

        String destination = productOverviewReturn(returnQuery);
        try {
            productSvc.deleteProduct(productId);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            org.slf4j.LoggerFactory.getLogger(ProductController.class)
                .warn("商品 {} 刪除受關聯資料限制", productId, e);
            model.addFlashAttribute("errorMessage", "商品規格已被訂單、購物車、收藏、活動或採購資料引用，請改為下架");
            return destination;
        } catch (IllegalArgumentException e) {
            model.addFlashAttribute("errorMessage", e.getMessage());
            return destination;
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(ProductController.class)
                .error("商品 {} 刪除失敗", productId, e);
            model.addFlashAttribute("errorMessage", "商品刪除未完成，請重新整理後再試；若持續失敗，請提供商品 ID 給管理人員查詢。");
            return destination;
        }

        model.addFlashAttribute("success", "刪除成功");

        return destination;
    }

    // Only restore known overview filters; the destination always stays on this page.
    static String productOverviewReturn(String query) {
        var target = org.springframework.web.util.UriComponentsBuilder.fromPath("/product/listAllProduct");
        var allowed = java.util.Set.of("page", "size", "keyword", "searchProductName", "searchSkuName",
            "statusFilter", "stockFilter", "parentCategoryId", "categoryId", "vendorId", "origin",
            "minComments", "maxComments", "ratingFilter", "sortBy", "sortDirection");
        if (query != null && query.length() <= 8192) {
            try {
                var source = org.springframework.web.util.UriComponentsBuilder
                    .fromUriString("/product/listAllProduct?" + query).build().getQueryParams();
                source.forEach((key, values) -> {
                    if (allowed.contains(key) && !values.isEmpty()) {
                        String value = values.get(0);
                        target.queryParam(key, java.net.URLDecoder.decode(value == null ? "" : value,
                            java.nio.charset.StandardCharsets.UTF_8));
                    }
                });
            } catch (IllegalArgumentException ignored) {
                return "redirect:/product/listAllProduct";
            }
        }
        return "redirect:" + target.build().encode().toUriString();
    }

    /*
     * 提供商品分類下拉選單資料
     */
    private static boolean isLegacyProductPage(jakarta.servlet.http.HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/product/") && !java.util.Set.of("/product/editData","/product/saveModal","/product/saveModalWithImages","/product/name-availability").contains(path);
    }
    @ModelAttribute("productCategoryListData")
    protected List<ProductCategory> referenceProductCategoryListData(jakarta.servlet.http.HttpServletRequest request) {
        return isLegacyProductPage(request) ? productCategorySvc.getAll() : null;
    }

    /*
     * 提供廠商下拉選單資料
     */
    @ModelAttribute("vendorListData")
    protected List<VendorVO> referenceVendorListData(jakarta.servlet.http.HttpServletRequest request) {
        return isLegacyProductPage(request) ? vendorSvc.getAll() : null;
    }

    /*
     * 顯示全部商品
     */
    @GetMapping("/product/listAllProduct")
    public String listAllProduct(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "true") boolean searchProductName,
            @RequestParam(defaultValue = "true") boolean searchSkuName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String stockFilter,
            @RequestParam(required = false) Integer parentCategoryId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer vendorId,
            @RequestParam(defaultValue = "") String origin,
            @RequestParam(required = false) String minComments,
            @RequestParam(required = false) String maxComments,
            @RequestParam(required = false) String ratingFilter,
            @RequestParam(defaultValue = "") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductMinComments", defaultValue = "") String savedMinComments,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductMaxComments", defaultValue = "") String savedMaxComments,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductRatingFilter", defaultValue = "") String savedRatingFilter,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductPageSize", defaultValue = "10") int savedSize,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductHideOffline", defaultValue = "false") boolean savedHideOffline,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductStatus", defaultValue = "") String savedStatus,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductStockFilter", defaultValue = "all") String savedStockFilter,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductParentCategory", defaultValue = "") String savedParentCategory,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductCategory", defaultValue = "") String savedCategory,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductVendor", defaultValue = "") String savedVendor,
            @org.springframework.web.bind.annotation.CookieValue(value = "fruitudeProductOrigin", required = false) jakarta.servlet.http.Cookie savedOrigin,
            jakarta.servlet.http.HttpServletRequest request,
            ModelMap model) {

        keyword = ProductService.normalizeOverviewKeyword(keyword);
        // Migrate the previous checkbox preference when no new status preference exists yet.
        String status = statusFilter == null ? (savedStatus.isEmpty() ? (savedHideOffline ? "on" : "all") : savedStatus) : statusFilter;
          if (!java.util.Set.of("all", "on", "off", "discontinued").contains(status)) status = "all";
        String stock = ProductService.normalizeStockFilter(stockFilter == null ? savedStockFilter : stockFilter);
        if (!request.getParameterMap().containsKey("parentCategoryId")) parentCategoryId = savedFilterId(savedParentCategory);
        if (!request.getParameterMap().containsKey("categoryId")) categoryId = savedFilterId(savedCategory);
        if (!request.getParameterMap().containsKey("vendorId")) vendorId = savedFilterId(savedVendor);
        @SuppressWarnings("unchecked")
        var categories = (List<ProductCategory>) model.get("productCategoryListData");
        var parents = categories.stream().filter(c -> c.getParentCategory() == null).toList();
        Integer requestedParent = parentCategoryId;
        if (parents.stream().noneMatch(c -> c.getProductCategoryId().equals(requestedParent))) parentCategoryId = null;
        Integer selectedParent = parentCategoryId;
        var children = categories.stream().filter(c -> c.getParentCategory() != null
            && c.getParentCategory().getProductCategoryId().equals(selectedParent)).toList();
        if (categoryId != null) {
            Integer selectedCategory = categoryId;
            if (children.stream().noneMatch(c -> c.getProductCategoryId().equals(selectedCategory))) categoryId = null;
        }
        @SuppressWarnings("unchecked")
        var vendors = (List<VendorVO>) model.get("vendorListData");
        var originOptions = productSvc.getOverviewOrigins();
        var origins = originOptions.stream().map(com.fruitude.product.model.FrontCatalogService.SearchOption::value).toList();
        if (!request.getParameterMap().containsKey("origin")) {
            try { origin = java.net.URLDecoder.decode(savedOrigin == null ? "" : savedOrigin.getValue(), java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalid) { origin = ""; }
        }
        origin = origin.strip();
        if (!origins.contains(origin)) origin = "";
        model.addAttribute("originListData", originOptions);
        model.addAttribute("selectedOrigin", origin);
        Integer requestedVendor = vendorId;
        if (vendors.stream().noneMatch(v -> v.getVendorId().equals(requestedVendor))) vendorId = null;
        Integer minCount = ProductService.normalizeCommentCount(minComments == null ? savedMinComments : minComments);
        Integer maxCount = ProductService.normalizeCommentCount(maxComments == null ? savedMaxComments : maxComments);
        String rating = ProductService.normalizeRatingFilter(ratingFilter == null ? savedRatingFilter : ratingFilter);
        sortBy = ProductService.normalizeOverviewSort(sortBy);
        sortDirection = "desc".equals(sortDirection) ? "desc" : "asc";
        var result = productSvc.getOverviewPage(page, size == null ? savedSize : size, status,
              parentCategoryId, categoryId, vendorId, stock, minCount, maxCount, rating, sortBy, sortDirection, keyword, searchProductName, searchSkuName, origin);

        model.addAttribute("categoryChildrenByParent", categories.stream()
            .filter(c -> c.getParentCategory() != null)
            .collect(java.util.stream.Collectors.groupingBy(c -> c.getParentCategory().getProductCategoryId())));
        model.addAttribute("stockAlertLabels", java.util.Map.of(
            "正常", "正常", "低於安全庫存", "低安全", "庫存低於10", "庫<10", "庫存高於100", "庫>100",
            "待進貨超過50", "進>50", "待進貨超過100", "進>100",
            "待出貨超過50", "出>50", "待出貨超過100", "出>100", "未設安全庫存", "未設"));
        model.addAttribute("parentCategoryListData", parents);
        model.addAttribute("childCategoryListData", children);
        model.addAttribute("selectedParentCategoryId", parentCategoryId);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedVendorId", vendorId);

        model.addAttribute("keyword", keyword.strip());
        model.addAttribute("searchProductName", searchProductName);
        model.addAttribute("searchSkuName", searchSkuName);
        model.addAttribute("productListData", result.getContent());
        var overviewProductIds = result.getContent().stream().map(com.fruitude.product.model.ProductRepository.ProductOverview::getProductId).toList();
        model.addAttribute("overviewSkuNames", productSvc.getOverviewSkuNames(overviewProductIds));
        model.addAttribute("overviewImageIds", productSvc.getOverviewImageIds(overviewProductIds));
        model.addAttribute("overviewStockStatuses", productSvc.getOverviewStockStatuses(overviewProductIds));
        model.addAttribute("overviewLightweight", true);
        model.addAttribute("currentPage", result.getTotalPages() == 0 ? 1 : result.getNumber() + 1);
        model.addAttribute("productReturnQuery", request.getQueryString() == null ? "" : request.getQueryString());
        model.addAttribute("pageSize", result.getSize());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalProducts", result.getTotalElements());
        model.addAttribute("statusFilter", status);
        model.addAttribute("stockFilter", stock);
        model.addAttribute("minComments", minCount);
        model.addAttribute("maxComments", maxCount);
        model.addAttribute("ratingFilter", rating);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDirection", sortDirection);

        return "admin/productmanagement/product/listAllProduct";
    }

    private static Integer savedFilterId(String value) {
        try {
            int id = Integer.parseInt(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /*
     * 查詢單筆商品
     */
    @GetMapping("/product/getOne_For_Display")
    public String displayProductDetails(@RequestParam("productId") Integer productId, ModelMap model) {
        return getOne_For_Display(productId, model);
    }

    @PostMapping("/product/getOne_For_Display")
    public String getOne_For_Display(
            @RequestParam("productId") Integer productId,
            ModelMap model) {

        Product product =
                productSvc.getOneProduct(productId);

        if (product == null) {
            return "redirect:/product/listAllProduct";
        }

        // 收集此商品所有 SKU 的圖片
        List<ProductImage> productImageList = new ArrayList<>();

        if (product.getProductSkus() != null) {
            for (ProductSku sku : product.getProductSkus()) {
                if (sku.getProductImages() != null) {
                    for (ProductImage image : sku.getProductImages()) {
                        if (image != null && image.getImageId() != null) {
                            productImageList.add(image);
                        }
                    }
                }
            }
        }

        model.addAttribute("product", product);
        model.addAttribute("productSkuList", product.getProductSkus());
        model.addAttribute("productImageList", productImageList);

        return "admin/productmanagement/product/listOneProduct";
    }

    // ProductController endpoints



    @ResponseBody
    @GetMapping("/product/editData") public ProductService.EditData load(@RequestParam Integer productId) { return productSvc.load(productId); }
    @ResponseBody
    @PostMapping("/product/saveModal") public ProductService.Saved save(@RequestBody ProductService.SaveRequest request) { return productSvc.save(request); }
    @ResponseBody
    @PostMapping(value="/product/saveModalWithImages", consumes="multipart/form-data")
    public ProductService.Saved saveImages(@RequestParam String payload,
            @RequestParam(required=false) List<org.springframework.web.multipart.MultipartFile> files) throws java.io.IOException {
        var request=tools.jackson.databind.json.JsonMapper.builder().build().readValue(payload,ProductService.ImageRequest.class);
        var uploads=new ArrayList<ProductImage>();
        var invalid=new ArrayList<InvalidImage>();
        var selected=files==null?List.<org.springframework.web.multipart.MultipartFile>of():files;
        for(int index=0;index<selected.size();index++) {
            var file=selected.get(index);
            try{
                var image=new ProductImage();com.fruitude.product.model.ProductImageService.ImageUploadSupport.apply(image,file);uploads.add(image);
            }catch(IOException|IllegalArgumentException error){
                invalid.add(new InvalidImage(index,uploadName(file),error instanceof IOException?"圖片內容無法讀取":error.getMessage()));
            }
        }
        if(!invalid.isEmpty())throw new ImageValidationException(invalid);
        return productSvc.saveWithImages(request,uploads);
    }
    private static String uploadName(MultipartFile file){
        String name=file==null?null:file.getOriginalFilename();
        if(name==null||name.isBlank())return "未命名圖片";
        name=name.replace('\\','/');return name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_");
    }
    private record InvalidImage(int fileIndex,String filename,String message) {}
    private static final class ImageValidationException extends IllegalArgumentException {
        private final List<InvalidImage> images;
        private ImageValidationException(List<InvalidImage> images){super("圖片驗證未通過");this.images=List.copyOf(images);}
    }
    @ExceptionHandler(ImageValidationException.class) public ResponseEntity<?> invalidImages(ImageValidationException error){
        String message=error.images.stream().map(image->"「"+image.filename()+"」："+image.message()).collect(java.util.stream.Collectors.joining("；"));
        return ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore())
            .body(Map.of("kind","invalidImages","message",message+"。不合格的新增圖已移除，換圖已恢復原圖，請重新選擇後再儲存。","invalidImages",error.images));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,tools.jackson.core.JacksonException.class})
    public ResponseEntity<?> invalidPayload(Exception e) { return error(400,"invalid","輸入格式不正確：價格須為1～10,000,000的整數，數量須為0～2,147,483,647的整數"); }
    @ExceptionHandler(java.io.IOException.class) public ResponseEntity<?> imageReadFailure(java.io.IOException e) { return error(400,"invalid","圖片讀取失敗，請重新選擇檔案"); }
    @ExceptionHandler(com.fruitude.product.model.ProductStatusAccess.ProductStatusAccessException.class) public ResponseEntity<?> denied(com.fruitude.product.model.ProductStatusAccess.ProductStatusAccessException e) { return error(403,"denied",e.getMessage()); }
    @ExceptionHandler(com.fruitude.product.model.ProductLifecycleService.ProductStatusConfirmationException.class) public ResponseEntity<?> confirm(com.fruitude.product.model.ProductLifecycleService.ProductStatusConfirmationException e) { return error(409,"confirm",e.getMessage()); }
    @ExceptionHandler(ProductService.Conflict.class) public ResponseEntity<?> conflict(ProductService.Conflict e) { return error(409,"conflict",e.getMessage()); }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<?> missing(NoSuchElementException e) { return error(404,"missing",e.getMessage()); }
    @ExceptionHandler(com.fruitude.product.model.ProductLifecycleService.SkuRestockConfirmationException.class) public ResponseEntity<?> restock(com.fruitude.product.model.ProductLifecycleService.SkuRestockConfirmationException e) { return error(409,"restock",e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) { return error(400,"invalid",e.getMessage()); }
    private ResponseEntity<?> error(int status,String kind,String message) { return ResponseEntity.status(status).body(Map.of("kind",kind,"message",message)); }

    // ProductController endpoints

}
