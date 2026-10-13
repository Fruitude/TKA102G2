package com.fruitude.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminIndexController {
	
	@GetMapping({"", "/"})
	public String index(Model model) {
		
		return "admin/index"; //view
	}
	
	@GetMapping("/psi")
	public String psi(Model model) {
		
		return "admin/psi/index"; //view
	}
	@GetMapping("/account")
	public String account(Model model) {
		
		return "admin/account/index"; //view
	}
	
	@GetMapping({"/psi/", "/psi/index", "/psi/index.html"})
	public String oldPsi() { return "redirect:/admin/psi"; }


    @GetMapping("/product")
    public String product() { return "admin/productmanagement/product"; }

    @GetMapping({"/psi/productmanagement.html", "/psi/productmanagement/content", "/psi/product", "/product.html", "/product/", "/psi/product.html", "/psi/product/"})
    public String oldProductManagement() { return "redirect:/admin/product"; }

    @GetMapping({"/psi/product/search", "/psi/product/stock"})
    public String oldProductQuery(jakarta.servlet.http.HttpServletRequest request) {
        String page = request.getRequestURI().endsWith("/stock") ? "stock" : "search";
        String query = request.getQueryString();
        return "redirect:/admin/product/" + page + (query == null ? "" : "?" + query);
    }

}
