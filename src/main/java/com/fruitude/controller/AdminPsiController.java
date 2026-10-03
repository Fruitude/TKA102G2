package com.fruitude.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/psi")
public class AdminPsiController {
	
	@GetMapping("/vendor")
	public String vendor(Model model) {
		System.out.println("執行vendor導向");
		
		return "admin/psi/vendor/index"; //view
	}
	
	@GetMapping("/purchase")
	public String purchase(Model model) {
		System.out.println("執行purchase導向");
		
		return "admin/psi/purchase/index"; //view
	}
	
	@GetMapping("/shipping")
	public String shipping(Model model) {
		System.out.println("執行vendor導向");
		
		return "admin/psi/shipping/index"; //view
	}
	
	@GetMapping("/receiving")
	public String receiving(Model model) {
		System.out.println("執行receiving導向");
		
		return "admin/psi/receiving/index"; //view
	}
	
	@GetMapping("/stock")
	public String stock(Model model) {
		System.out.println("執行order導向");
		
		return "admin/psi/stock/index"; //view
	}

}
