package com.fruitude.po.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.po.model.PoService;
import com.fruitude.po.model.PoVO;
import com.fruitude.vendor.model.VendorVO;

@Controller
@RequestMapping("/admin/psi/po")
public class PoController {
	
	@Autowired
	PoService poSvc;
	
	@RequestMapping("/listAllPo")
	public String listAllVendor(Model model,
	        @RequestParam(value = "inboundStatus", required = false) Byte inboundStatus) {

	    List<PoVO> pos = (inboundStatus == null)
	            ? poSvc.getAll()
	            : poSvc.getByInboundStatus(inboundStatus);

	    if (pos.isEmpty()) {
	        model.addAttribute("errorMessage", "查無資料");
	        return "admin/psi/purchase/index";
	    }
	    model.addAttribute("inboundStatus", inboundStatus);
	    model.addAttribute("poListData", pos);
	    return "admin/psi/purchase/listAllPo";
	}
	
	

}
