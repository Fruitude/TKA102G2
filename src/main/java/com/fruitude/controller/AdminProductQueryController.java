package com.fruitude.controller;

import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.fruitude.product.model.ProductService;
import com.fruitude.product.model.ProductSkuService;

@Controller
@RequestMapping("/admin/psi/product")
public class AdminProductQueryController {
    private final ProductService products;
    private final ProductSkuService skus;

    public AdminProductQueryController(ProductService products, ProductSkuService skus) {
        this.products = products;
        this.skus = skus;
    }

    private boolean matches(Object value, String keyword) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT).contains(keyword);
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String keyword, Model model) {
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("products", products.getAll().stream().filter(p -> query.isEmpty()
            || Objects.toString(p.getProductId(), "").equals(query) || matches(p.getProductName(), query)).toList());
        return "admin/psi/productmanagement/product/search";
    }

    @GetMapping("/stock")
    public String stock(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "false") boolean belowOnly, Model model) {
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("belowOnly", belowOnly);
        model.addAttribute("skus", skus.getAll().stream()
            .filter(s -> query.isEmpty() || Objects.toString(s.getSkuId(), "").equals(query)
                || matches(s.getSkuName(), query)
                || (s.getProduct() != null && matches(s.getProduct().getProductName(), query)))
            .filter(s -> !belowOnly || s.isBelowSafetyStock()).toList());
        return "admin/psi/productmanagement/product/stock";
    }
}
