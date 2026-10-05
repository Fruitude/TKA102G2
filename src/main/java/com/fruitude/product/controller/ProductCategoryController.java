package com.fruitude.product.controller;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.product.model.ProductCategory;
import com.fruitude.product.model.ProductCategoryService;
import com.fruitude.product.model.ProductService;

@Controller
@RequestMapping("/productcategory")
public class ProductCategoryController {
    @Autowired private ProductCategoryService productCategoryService;
    @Autowired private ProductService productService;

    @InitBinder("productCategory")
    void bind(WebDataBinder binder) { binder.setAllowedFields("productCategoryId", "categoryName", "categoryDesc", "sortOrder", "status"); }
    @ModelAttribute
    void referenceData(Model model) { model.addAttribute("categoryList", productCategoryService.getAll()); }
    @GetMapping("/list")
    public String list() { return "admin/psi/productmanagement/productcategory/list"; }
    @GetMapping({"/add", "/addProductCategory"})
    public String add(Model model) {
        model.addAttribute("productCategory", new ProductCategory());
        return "admin/psi/productmanagement/productcategory/add";
    }
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model, RedirectAttributes redirect) {
        ProductCategory category = productCategoryService.getOneProductCategory(id);
        if (category == null) { redirect.addFlashAttribute("errorMessage", "分類不存在"); return "redirect:/productcategory/list"; }
        model.addAttribute("productCategory", category);
        model.addAttribute("parentCategoryId", category.getParentCategory() == null ? null : category.getParentCategory().getProductCategoryId());
        return "admin/psi/productmanagement/productcategory/edit";
    }
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model, RedirectAttributes redirect) {
        ProductCategory category = productCategoryService.getOneProductCategory(id);
        if (category == null) { redirect.addFlashAttribute("errorMessage", "分類不存在"); return "redirect:/productcategory/list"; }
        model.addAttribute("productCategory", category);
        return "admin/psi/productmanagement/productcategory/detail";
    }
    private ProductCategory validate(ProductCategory form, Integer parentId, BindingResult result) {
        if (form.getCategoryName() == null || form.getCategoryName().trim().isEmpty() || form.getCategoryName().trim().length() > 50)
            result.rejectValue("categoryName", "invalid", "分類名稱須為 1～50 字");
        else form.setCategoryName(form.getCategoryName().trim());
        if (form.getCategoryDesc() != null && form.getCategoryDesc().length() > 255) result.rejectValue("categoryDesc", "invalid", "描述不可超過 255 字");
        if (form.getSortOrder() == null || form.getSortOrder() < 0) result.rejectValue("sortOrder", "invalid", "排序不可小於 0");
        if (form.getStatus() == null || form.getStatus() < 0 || form.getStatus() > 1) result.rejectValue("status", "invalid", "分類狀態須為 0 或 1");
        ProductCategory parent = parentId == null ? null : productCategoryService.getOneProductCategory(parentId);
        if (parentId != null && parent == null) result.reject("parent", "父分類不存在");
        Set<Integer> visited = new HashSet<>();
        for (ProductCategory cursor = parent; cursor != null; cursor = cursor.getParentCategory()) {
            if (Objects.equals(cursor.getProductCategoryId(), form.getProductCategoryId()) || !visited.add(cursor.getProductCategoryId())) {
                result.reject("parent", "不能將自己或子孫分類設為父分類"); break;
            }
        }
        for (ProductCategory category : productCategoryService.getAll()) {
            Integer existingParent = category.getParentCategory() == null ? null : category.getParentCategory().getProductCategoryId();
            if (!Objects.equals(category.getProductCategoryId(), form.getProductCategoryId()) && Objects.equals(existingParent, parentId)
                && form.getCategoryName() != null && form.getCategoryName().equalsIgnoreCase(category.getCategoryName())) {
                result.rejectValue("categoryName", "duplicate", "同父分類下已有相同名稱"); break;
            }
        }
        return parent;
    }
    @PostMapping("/insert")
    public String insert(@ModelAttribute("productCategory") ProductCategory form, BindingResult result,
        @RequestParam(required = false) Integer parentCategoryId, Model model, RedirectAttributes redirect) {
        form.setProductCategoryId(null);
        ProductCategory parent = validate(form, parentCategoryId, result);
        model.addAttribute("parentCategoryId", parentCategoryId);
        if (result.hasErrors()) return "admin/psi/productmanagement/productcategory/add";
        form.setParentCategory(parent);
        try { productCategoryService.addProductCategory(form); }
        catch (DataIntegrityViolationException e) { result.reject("save", "分類名稱重複，或父分類已變更"); return "admin/psi/productmanagement/productcategory/add"; }
        redirect.addFlashAttribute("successMessage", "分類新增成功");
        return "redirect:/productcategory/list";
    }
    @PostMapping("/update")
    public String update(@ModelAttribute("productCategory") ProductCategory form, BindingResult result,
        @RequestParam(required = false) Integer parentCategoryId, Model model, RedirectAttributes redirect) {
        ProductCategory original = form.getProductCategoryId() == null ? null : productCategoryService.getOneProductCategory(form.getProductCategoryId());
        if (original == null) { redirect.addFlashAttribute("errorMessage", "分類不存在"); return "redirect:/productcategory/list"; }
        ProductCategory parent = validate(form, parentCategoryId, result);
        model.addAttribute("parentCategoryId", parentCategoryId);
        if (result.hasErrors()) return "admin/psi/productmanagement/productcategory/edit";
        original.setCategoryName(form.getCategoryName());
        original.setCategoryDesc(form.getCategoryDesc());
        original.setSortOrder(form.getSortOrder());
        original.setStatus(form.getStatus());
        original.setParentCategory(parent);
        original.setUpdatedAt(LocalDateTime.now());
        try { productCategoryService.updateProductCategory(original); }
        catch (DataIntegrityViolationException e) { result.reject("save", "分類名稱重複，或關聯資料已變更"); return "admin/psi/productmanagement/productcategory/edit"; }
        redirect.addFlashAttribute("successMessage", "分類修改成功");
        return "redirect:/productcategory/list";
    }
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Integer id, RedirectAttributes redirect) {
        if (productCategoryService.getOneProductCategory(id) == null) redirect.addFlashAttribute("errorMessage", "分類不存在");
        else if (productCategoryService.getAll().stream().anyMatch(c -> c.getParentCategory() != null && id.equals(c.getParentCategory().getProductCategoryId()))
            || productService.getAll().stream().anyMatch(p -> p.getProductCategory() != null && id.equals(p.getProductCategory().getProductCategoryId())))
            redirect.addFlashAttribute("errorMessage", "此分類仍有子分類或商品引用，不能刪除");
        else {
            try { productCategoryService.deleteProductCategory(id); redirect.addFlashAttribute("successMessage", "分類刪除成功"); }
            catch (DataIntegrityViolationException e) { redirect.addFlashAttribute("errorMessage", "此分類仍被其他資料引用，不能刪除"); }
        }
        return "redirect:/productcategory/list";
    }
}