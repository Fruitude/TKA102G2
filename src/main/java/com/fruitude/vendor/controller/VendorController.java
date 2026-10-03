package com.fruitude.vendor.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fruitude.vendor.model.VendorService;





@Controller
@RequestMapping("/admin/psi/vendor/")
public class VendorController {
	
	@Autowired
	VendorService vendorSvc;
	
	@GetMapping("/listAllVendor")
	public String listAllVendor(Model model) {
		System.out.println("執行listAllVendor導向");
		
		model.addAttribute("vendorListData", vendorSvc.getAll());

		return "admin/psi/vendor/listAllVendor"; //view
	}


	
	@InitBinder
	public void initBinder(WebDataBinder binder) {
	    // true 代表：空字串或只有空白的字串，轉成 null
	    binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}

}
