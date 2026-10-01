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
		System.out.println("執行index導向");
		
		return "admin/index"; //view
	}
    
	@GetMapping("/psi")
	public String psi(Model model) {
		System.out.println("執行psi導向");
		
		return "admin/psi/index"; //view
	}
    
	@GetMapping("/account")
	public String account(Model model) {
		System.out.println("執行account導向");
		
		return "admin/account/index"; //view
	}
	
	

}
