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
		System.out.println("執行index導向");
		
		return "admin/index"; //view
	}
	
	@GetMapping("/psi")
	public String psi(Model model) {
		System.out.println("執行psi導向");
		
		return "admin/psi/index"; //view
	}
	
    @GetMapping("/psi/product")
    public String product() { return "admin/psi/product"; }

    @GetMapping({"/psi/productmanagement.html", "/psi/productmanagement/content", "/product", "/product.html", "/product/", "/psi/product.html", "/psi/product/"})
    public String oldProductManagement() { return "redirect:/admin/psi/product"; }

    @GetMapping({"/vendor", "/vendor.html", "/vendor/", "/psi/vendor.html"})
    public String oldVendor() { return "redirect:/admin/psi/vendor"; }

    @GetMapping({"/psi/", "/psi/index", "/psi/index.html"})
    public String oldPsi() { return "redirect:/admin/psi"; }

    @GetMapping("/account")
	public String account(Model model) {
		System.out.println("執行account導向");
		
		return "admin/account/index"; //view
	}
	
	

}
