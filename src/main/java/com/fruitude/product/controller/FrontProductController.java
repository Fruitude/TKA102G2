package com.fruitude.product.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.fruitude.product.model.FrontCatalogService;
import com.fruitude.product.model.FrontCatalogService.ProductView;
import com.fruitude.orders.model.OrdersService;
import com.fruitude.promo.model.PromoService;
import jakarta.servlet.http.HttpSession;

@Controller
public class FrontProductController {
    private final FrontCatalogService catalog;
    private final PromoService promoService;
    private final OrdersService ordersService;
    public FrontProductController(FrontCatalogService catalog, PromoService promoService, OrdersService ordersService) {
        this.catalog = catalog;
        this.promoService = promoService;
        this.ordersService = ordersService;
    }

    @GetMapping("/front")
    public String canonicalHome() { return "redirect:/front/"; }

    @GetMapping({"/front/", "/front/index", "/front/index.html"})
    public String homePage(Model model, HttpSession session) {
        home(model);
        // 首頁活動區塊：限時活動與會員專屬福利。會員身分只信任 session，用來標示目前符合資格的福利（壽星月、新會員首購）
        Integer memberId = session.getAttribute("loggedInMemberId") instanceof Integer id ? id : null;
        var promos = promoService.findHomePromos(memberId == null
                ? com.fruitude.promo.model.MemberPromoState.ANONYMOUS : ordersService.memberPromoState(memberId));
        model.addAttribute("limitedPromos", promos.limited());
        model.addAttribute("memberPerks", promos.perks());
        return "front/index";
    }

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
    public String detail(@RequestParam(required = false) Integer productId, @RequestParam(required = false) String name, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        List<ProductView> products = catalog.getProducts();
        Integer id = productId != null ? productId : products.stream().filter(p -> name != null && name.equals(p.name())).map(ProductView::productId).findFirst().orElse(null);
        ProductView selected = catalog.getLiveProduct(id);
        model.addAttribute("dbProduct", selected);
        model.addAttribute("relatedProducts", products.stream().filter(p -> selected != null && !p.productId().equals(selected.productId())).limit(6).toList());
        return "front/product/view/index";
    }

    @GetMapping("/front/api/cart-products")
    @ResponseBody
    public org.springframework.http.ResponseEntity<List<FrontCatalogService.LiveSku>> cartProducts(@RequestParam List<Integer> skuIds) {
        if (skuIds.size() > 500 || skuIds.stream().anyMatch(id -> id == null || id <= 0))
            return org.springframework.http.ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore()).build();
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(catalog.getLiveSkus(skuIds));
    }
}
