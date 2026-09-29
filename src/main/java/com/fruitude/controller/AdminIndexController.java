package com.fruitude.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminIndexController {
	
	@GetMapping("")
	public String index(Model model) {
		
		return "admin/index"; //view
	}
    
	@GetMapping("/vendor")
	public String vendor(Model model) {
		
		return "admin/vendor/vendor"; //view
	}
    
	@GetMapping("/account")
	public String account(Model model) {
		
		return "admin/account/account"; //view
	}
	
	

}
