package com.fruitude.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/account")
public class AdminAccountController {
	
	@GetMapping("/purchasereview")
	public String purchasereview(Model model) {
	    return "admin/account/purchasereview/index";
	}
	
	@GetMapping("/refundreview")
	public String refundreview(Model model) {
	    return "admin/account/refundreview/index";
	}

}
