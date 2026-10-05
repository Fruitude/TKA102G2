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

@Controller
@RequestMapping("/product")
public class ProductController {

    @org.springframework.web.bind.annotation.InitBinder("product")
    void bind(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("productId", "productName", "productDesc", "status", "vendor.vendorId", "productCategory.productCategoryId",
            "productSkus[*].skuName", "productSkus[*].anotherName", "productSkus[*].price", "productSkus[*].stock", "productSkus[*].safetyStock", "productSkus[*].status");
    }

    private void validate(Product p, BindingResult result) {
        if (p.getProductName() == null || p.getProductName().trim().isEmpty() || p.getProductName().trim().length() > 100)
            result.rejectValue("productName", "invalid", "商品名稱須為 1～100 字");
        else p.setProductName(p.getProductName().trim());
        if (p.getProductDesc() != null && p.getProductDesc().length() > 255) result.rejectValue("productDesc", "invalid", "描述不可超過 255 字");
        if (p.getStatus() == null || p.getStatus() < 0 || p.getStatus() > 3) result.rejectValue("status", "invalid", "狀態須為 0～3");
        Integer categoryId = p.getProductCategory() == null ? null : p.getProductCategory().getProductCategoryId();
        Integer vendorId = p.getVendor() == null ? null : p.getVendor().getVendorId();
        ProductCategory category = categoryId == null ? null : productCategorySvc.getOneProductCategory(categoryId);
        VendorVO vendor = vendorId == null ? null : vendorSvc.getOneVendor(vendorId);
        if (category == null) result.reject("category", "請選擇有效分類"); else p.setProductCategory(category);
        if (vendor == null) result.reject("vendor", "請選擇有效廠商"); else p.setVendor(vendor);
        if (vendor != null && p.getProductName() != null && productSvc.isNameUsed(vendorId, p.getProductName(), p.getProductId()))
            result.rejectValue("productName", "duplicate", "此廠商已有相同商品名稱");
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
    @GetMapping("addProduct")
    public String addProduct(ModelMap model) {
        Product product = new Product();
        product.getProductSkus().add(new ProductSku());
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
    @PostMapping("insert")
    public String insert(
            @Valid @ModelAttribute("product") Product product,
            BindingResult result,
            MultipartHttpServletRequest multipartRequest,
            ModelMap model) {

        product.setProductId(null);
        validate(product, result);
        java.util.Set<String> names = new java.util.HashSet<>();
        if (product.getProductSkus().isEmpty()) result.reject("sku", "至少新增一個規格");
        for (ProductSku sku : product.getProductSkus()) {
            String error = ProductSkuController.validateProductSku(sku);
            if (error != null) result.reject("sku", error);
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

        	    MultipartFile file = multipartRequest.getFile("skuImages[" + i + "]");

        	    if (file != null) {

        	        if (file != null && !file.isEmpty()) {

        	            try {

        	                ProductImage productImage = new ProductImage();

        	                // 圖片 → SKU
        	                productImage.setProductSku(sku);

        	                // 圖片內容
        	                productImage.setImageData(file.getBytes());

        	                // 圖片檔名
        	                productImage.setImageName(
        	                    file.getOriginalFilename()
        	                );

        	                // MIME Type
        	                productImage.setImageType(
        	                    file.getContentType()
        	                );

        	                // 檔案大小
        	                productImage.setFileSize(
        	                    (int) file.getSize()
        	                );

        	                // 排序
        	                productImage.setSortOrder(0);

        	                // 建立時間
        	                productImage.setCreatedAt(
        	                    LocalDateTime.now()
        	                );

        	                // 加入 SKU
                            ImageUploadSupport.apply(productImage, file);
        	                sku.getProductImages().add(productImage);

        	            } catch (IOException | IllegalArgumentException e) {

        	                e.printStackTrace();

        	                model.addAttribute(
        	                    "errorMessage",
        	                    e instanceof IOException ? "圖片上傳失敗" : e.getMessage()
        	                );

        	                return "admin/productmanagement/product/addProduct";
        	            }
        	        }
        	    }
        	}
        }

        // 新增資料
        try { productSvc.addProduct(product); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            result.reject("save", "商品或規格名稱重複，或關聯資料已變更");
            return "admin/productmanagement/product/addProduct";
        }

        // 新增完成，準備轉交
        model.addAttribute("success", "- (新增成功)");

        return "redirect:/product/listAllProduct";
    }

    /*
     * 查詢單筆商品，準備修改
     */
    @PostMapping("getOne_For_Update")
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
    @PostMapping("update")
    public String update(
            @Valid @ModelAttribute("product") Product product,
            BindingResult result,
            ModelMap model) {

        if (product.getProductId() == null || productSvc.getOneProduct(product.getProductId()) == null) return "redirect:/product/listAllProduct";
        validate(product, result);

        // 輸入格式驗證失敗，回到修改頁面
        if (result.hasErrors()) {
            return "admin/productmanagement/product/update_product_input";
        }

        // 修改資料
        try { productSvc.updateBasicFields(product); }
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
    
    @PostMapping("updateStatus")
    @ResponseBody
    public String updateStatus(
            @RequestParam("productId") Integer productId,
            @RequestParam("status") Byte status) {

        if (status < 0 || status > 3) return "狀態無效";

        Product product =
                productSvc.getOneProduct(productId);

        if (product == null) {
            return "商品不存在";
        }

        productSvc.updateStatus(productId, status);

        return "success";
    }

    @PostMapping("updatePageStatus")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> updatePageStatus(
            @RequestParam List<Integer> productIds, @RequestParam Byte status) {
        try {
            int updated = productSvc.updatePageStatus(productIds, status);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("updated", updated));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    /*
     * 刪除商品
     */
    @PostMapping("delete")
    public String delete(
            @RequestParam("productId") Integer productId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes model) {

        try {
            if (productSvc.getOneProduct(productId) == null) {
                model.addFlashAttribute("errorMessage", "商品不存在");
                return "redirect:/product/listAllProduct";
            }
            productSvc.deleteProduct(productId);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            model.addFlashAttribute("errorMessage", "商品規格已被訂單、購物車或其他資料引用，請改為下架");
            return "redirect:/product/listAllProduct";
        }

        model.addFlashAttribute("success", "刪除成功");

        return "redirect:/product/listAllProduct";
    }

    /*
     * 提供商品分類下拉選單資料
     */
    @ModelAttribute("productCategoryListData")
    protected List<ProductCategory> referenceProductCategoryListData() {
        return productCategorySvc.getAll();
    }

    /*
     * 提供廠商下拉選單資料
     */
    @ModelAttribute("vendorListData")
    protected List<VendorVO> referenceVendorListData() {
        return vendorSvc.getAll();
    }

    /*
     * 顯示全部商品
     */
    @GetMapping("listAllProduct")
    public String listAllProduct(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String stockFilter,
            @RequestParam(required = false) Integer parentCategoryId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer vendorId,
            @RequestParam(required = false) String minComments,
            @RequestParam(required = false) String maxComments,
            @RequestParam(required = false) String ratingFilter,
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
            jakarta.servlet.http.HttpServletRequest request,
            ModelMap model) {

        // Migrate the previous checkbox preference when no new status preference exists yet.
        String status = statusFilter == null ? (savedStatus.isEmpty() ? (savedHideOffline ? "on" : "all") : savedStatus) : statusFilter;
          if (!java.util.Set.of("all", "on", "off").contains(status)) status = "all";
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
        Integer requestedVendor = vendorId;
        if (vendors.stream().noneMatch(v -> v.getVendorId().equals(requestedVendor))) vendorId = null;
        Integer minCount = ProductService.normalizeCommentCount(minComments == null ? savedMinComments : minComments);
        Integer maxCount = ProductService.normalizeCommentCount(maxComments == null ? savedMaxComments : maxComments);
        String rating = ProductService.normalizeRatingFilter(ratingFilter == null ? savedRatingFilter : ratingFilter);
        var result = productSvc.getOverviewPage(page, size == null ? savedSize : size, status,
              parentCategoryId, categoryId, vendorId, stock, minCount, maxCount, rating);

        model.addAttribute("parentCategoryListData", parents);
        model.addAttribute("childCategoryListData", children);
        model.addAttribute("selectedParentCategoryId", parentCategoryId);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedVendorId", vendorId);

        model.addAttribute("productListData", result.getContent());
        var overviewProductIds = result.getContent().stream().map(com.fruitude.product.model.ProductOverview::getProductId).toList();
        model.addAttribute("overviewImageIds", productSvc.getOverviewImageIds(overviewProductIds));
        model.addAttribute("overviewStockStatuses", productSvc.getOverviewStockStatuses(overviewProductIds));
        model.addAttribute("overviewLightweight", true);
        model.addAttribute("currentPage", result.getTotalPages() == 0 ? 1 : result.getNumber() + 1);
        model.addAttribute("pageSize", result.getSize());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalProducts", result.getTotalElements());
        model.addAttribute("statusFilter", status);
        model.addAttribute("stockFilter", stock);
        model.addAttribute("minComments", minCount);
        model.addAttribute("maxComments", maxCount);
        model.addAttribute("ratingFilter", rating);

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
    @PostMapping("getOne_For_Display")
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
}
