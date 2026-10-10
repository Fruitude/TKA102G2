package com.fruitude.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/account")
public class AdminAccountController {
	
	// 採購單審核首頁（/admin/account/purchasereview）改由 PoReviewController 的 purchasereview 處理

	@GetMapping("/refundreview")
	public String refundreview(Model model) {
	    return "admin/account/refundreview/index";
	}

}
