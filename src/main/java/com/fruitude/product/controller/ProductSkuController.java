package com.fruitude.product.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.product.model.Product;
import com.fruitude.product.model.ProductService;
import com.fruitude.product.model.ProductSku;
import com.fruitude.product.model.ProductSkuService;

@Controller
@RequestMapping("/productsku")
public class ProductSkuController {

    @org.springframework.web.bind.annotation.InitBinder("productSku")
    void bind(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("skuId", "product.productId", "skuName", "anotherName", "price", "stock", "safetyStock", "inboundQty", "outboundQty", "status");
    }

    @GetMapping("/detail")
    public String detail(@RequestParam Integer skuId, ModelMap model, RedirectAttributes redirect) {
        ProductSku sku = productSkuSvc.getOneProductSku(skuId);
        if (sku == null) { redirect.addFlashAttribute("errorMsg", "規格不存在"); return "redirect:/productsku/listAllProductSku"; }
        model.addAttribute("productSku", sku);
        return "admin/productmanagement/productsku/detail";
    }

    @Autowired
    private ProductSkuService productSkuSvc;

    @Autowired
    private ProductService productSvc;

    // 共用：準備新增/修改表單需要的商品清單
    private void addProductList(ModelMap model) {
        List<Product> productList = productSvc.getAll();
        model.addAttribute("productListData", productList);
    }

    // 規格列表
    @GetMapping("/listAllProductSku")
    public String listAllProductSku(ModelMap model) {
        List<ProductSku> productSkuList = productSkuSvc.getAll();
        model.addAttribute("productSkuList", productSkuList);

        return "admin/productmanagement/productsku/listAllProductSku";
    }

    // 顯示新增規格頁
    @GetMapping("/addProductSku")
    public String addProductSku(ModelMap model) {
        model.addAttribute("productSku", new ProductSku());
        addProductList(model);

        return "admin/productmanagement/productsku/addProductSku";
    }

    // 新增規格
    @PostMapping("/insert")
    public String insert(
            @ModelAttribute("productSku") ProductSku productSku,
            BindingResult result,
            @RequestParam("product.productId") Integer productId,
            ModelMap model,
            RedirectAttributes redirectAttributes) {

        productSku.setSkuId(null);
        String errorMsg = validateProductSku(productSku);
        if (result.hasErrors()) errorMsg = appendError(errorMsg, "欄位格式錯誤，請輸入有效數值。");

        Product product = productSvc.getOneProduct(productId);

        if (product == null) {
            errorMsg = appendError(errorMsg, "請選擇有效的商品。");
        }

        if (product != null
                && productSku.getSkuName() != null
                && !productSku.getSkuName().trim().isEmpty()
                && productSkuSvc.isSkuNameExist(
                        productId, productSku.getSkuName().trim())) {
            errorMsg = appendError(errorMsg, "此商品底下已存在相同的規格名稱。");
        }

        if (errorMsg != null) {
            model.addAttribute("errorMsg", errorMsg);
            addProductList(model);
            return "admin/productmanagement/productsku/addProductSku";
        }

        productSku.setProduct(product);
        productSku.setSkuName(productSku.getSkuName().trim());
        productSku.setCreatedAt(LocalDateTime.now());

        try { productSkuSvc.addProductSku(productSku); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            model.addAttribute("errorMsg", "規格名稱重複，或商品已被刪除。");
            addProductList(model);
            return "admin/productmanagement/productsku/addProductSku";
        }

        redirectAttributes.addFlashAttribute("success", "規格新增成功");
        return "redirect:/productsku/listAllProductSku";
    }

    // 取得單筆規格，進入修改頁
    @GetMapping("/getOne_For_Update")
    public String getOneForUpdate(
            @RequestParam("skuId") Integer skuId,
            ModelMap model) {

        ProductSku productSku = productSkuSvc.getOneProductSku(skuId);

        if (productSku == null) {
            model.addAttribute("errorMsg", "找不到指定的商品規格。");
            model.addAttribute("productSkuList", productSkuSvc.getAll());
            return "admin/productmanagement/productsku/listAllProductSku";
        }

        model.addAttribute("productSku", productSku);
        addProductList(model);

        return "admin/productmanagement/productsku/updateProductSkuInput";
    }

    // 修改規格
    @PostMapping("/update")
    public String update(
            @ModelAttribute("productSku") ProductSku formSku,
            BindingResult result,
            @RequestParam("skuId") Integer skuId,
            ModelMap model,
            RedirectAttributes redirectAttributes) {

        ProductSku originalSku = productSkuSvc.getOneProductSku(skuId);

        if (originalSku == null) {
            model.addAttribute("errorMsg", "找不到要修改的商品規格。");
            model.addAttribute("productSkuList", productSkuSvc.getAll());
            return "admin/productmanagement/productsku/listAllProductSku";
        }

        String errorMsg = validateProductSku(formSku);
        if (result.hasErrors()) errorMsg = appendError(errorMsg, "欄位格式錯誤，請輸入有效數值。");

        String skuName = formSku.getSkuName() == null
                ? ""
                : formSku.getSkuName().trim();

        if (!skuName.isEmpty()
                && productSkuSvc.isSkuNameExistExcludeSkuId(
                        originalSku.getProduct().getProductId(),
                        skuName,
                        skuId)) {
            errorMsg = appendError(errorMsg, "此商品底下已存在相同的規格名稱。");
        }

        if (errorMsg != null) {
            formSku.setSkuId(skuId);
            formSku.setProduct(originalSku.getProduct());
            model.addAttribute("productSku", formSku);
            model.addAttribute("errorMsg", errorMsg);
            addProductList(model);
            return "admin/productmanagement/productsku/updateProductSkuInput";
        }

        formSku.setSkuId(skuId);
        formSku.setProduct(originalSku.getProduct());
        try { productSkuSvc.updateProductSku(formSku); }
        catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage()); addProductList(model);
            return "admin/productmanagement/productsku/updateProductSkuInput";
        }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            formSku.setProduct(originalSku.getProduct());
            model.addAttribute("errorMsg", "規格名稱重複，或關聯資料已變更。");
            addProductList(model);
            return "admin/productmanagement/productsku/updateProductSkuInput";
        }

        redirectAttributes.addFlashAttribute("success", "規格修改成功");
        return "redirect:/productsku/listAllProductSku";
    }

    // 刪除規格
    @PostMapping("/delete")
    public String delete(
            @RequestParam("skuId") Integer skuId,
            RedirectAttributes redirectAttributes) {

        try {
            if (productSkuSvc.getOneProductSku(skuId) == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "規格不存在");
                return "redirect:/productsku/listAllProductSku";
            }
            productSkuSvc.deleteProductSku(skuId);
            redirectAttributes.addFlashAttribute("success", "規格刪除成功");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "刪除失敗：此規格可能已被其他資料引用。");
        }

        return "redirect:/productsku/listAllProductSku";
    }

    // 安全庫存狀態查詢
    @GetMapping("/checkSafetyStock")
    public String checkSafetyStock(
            @RequestParam("skuId") Integer skuId,
            ModelMap model) {

        ProductSku productSku = productSkuSvc.getOneProductSku(skuId);

        if (productSku == null) {
            model.addAttribute("errorMsg", "找不到指定的商品規格。");
        } else {
            model.addAttribute("productSku", productSku);
            model.addAttribute(
                    "belowSafetyStock",
                    productSku.isBelowSafetyStock());
        }

        model.addAttribute("productSkuList", productSkuSvc.getAll());
        return "admin/productmanagement/productsku/listAllProductSku";
    }

    // 表單欄位基本檢查
    static String validateProductSku(ProductSku sku) {
        StringBuilder errors = new StringBuilder();

        if (sku.getSkuName() != null && sku.getSkuName().trim().length() > 50) errors.append("規格名稱不可超過 50 字。");
        if (sku.getAnotherName() != null && sku.getAnotherName().length() > 50) errors.append("別稱不可超過 50 字。");
        if (sku.getStatus() == null || sku.getStatus() < 0 || sku.getStatus() > 5) errors.append("狀態須為 0～5。");

        if (sku.getSkuName() == null || sku.getSkuName().trim().isEmpty()) {
            errors.append("規格名稱不可空白。");
        }

        if (sku.getPrice() == null || sku.getPrice() <= 0) {
            errors.append("價格必須大於 0。");
        }

        if (isNegative(sku.getStock())) {
            errors.append("庫存數量不可小於 0。");
        }

        if (isNegative(sku.getSafetyStock())) {
            errors.append("安全庫存量不可小於 0。");
        }

        if (isNegative(sku.getInboundQty())) {
            errors.append("待進貨數量不可小於 0。");
        }

        if (isNegative(sku.getOutboundQty())) {
            errors.append("待出貨數量不可小於 0。");
        }

        return errors.length() == 0 ? null : errors.toString();
    }

    private static boolean isNegative(Integer value) {
        return value == null || value < 0;
    }

    private String appendError(String current, String message) {
        return current == null ? message : current + message;
    }
}
