package com.fruitude.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fruitude.vendor.model.VendorService;

@Controller
@RequestMapping("/admin/psi")
public class AdminPsiController {
	

	@GetMapping("/vendor")
	public String vendor(Model model) {
	    return "admin/psi/vendor/index";
	}
	

	
	// 進貨系統首頁（/admin/psi/receiving）改由 ReceivingController 的 receiving 處理

	// 庫存管理系統首頁（/admin/psi/stock）改由 StockController 的 stock 處理

}
