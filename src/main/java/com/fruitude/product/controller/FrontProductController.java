package com.fruitude.product.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.fruitude.product.model.FrontCatalogService;
import com.fruitude.product.model.FrontCatalogService.ProductView;

@Controller
public class FrontProductController {
    private final FrontCatalogService catalog;
    public FrontProductController(FrontCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/front")
    public String canonicalHome() { return "redirect:/front/"; }

    @GetMapping({"/front/", "/front/index", "/front/index.html"})
    public String home(Model model) {
        List<ProductView> products = catalog.getProducts();
        model.addAttribute("freshProducts", products.stream().filter(p -> !p.giftBox()).toList());
        model.addAttribute("giftProducts", products.stream().filter(ProductView::giftBox).toList());
        return "front/index";
    }

    @GetMapping({"/front/all-products/", "/front/all-products/index", "/front/all-products/index.html"})
    public String allProducts(Model model) {
        home(model);
        return "front/all-products/index";
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
    public String detail(@RequestParam(required = false) Integer productId, @RequestParam(required = false) String name, Model model) {
        List<ProductView> products = catalog.getProducts();
        ProductView selected = products.stream().filter(p -> productId != null ? productId.equals(p.productId()) : name != null && name.equals(p.name())).findFirst().orElse(null);
        model.addAttribute("dbProduct", selected);
        model.addAttribute("relatedProducts", products.stream().filter(p -> selected != null && !p.productId().equals(selected.productId())).limit(6).toList());
        return "front/product/view/index";
    }
}
