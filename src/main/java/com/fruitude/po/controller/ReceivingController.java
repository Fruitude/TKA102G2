package com.fruitude.po.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.po.model.PoVO;
import com.fruitude.po.model.ReceivingService;

// 進貨系統：和採購單共用 PoVO、PoDetailVO，處理審核通過後的到貨、驗收、入庫
@Controller
@RequestMapping("/admin/psi/receiving")
public class ReceivingController {

	@Autowired
	ReceivingService receivingSvc;

	// 進貨系統首頁：列出申請通過、尚未驗收的採購單
	@GetMapping("")
	public String receiving(Model model) {
		model.addAttribute("poListData", receivingSvc.getPendingInboundPos());
		return "admin/psi/receiving/index"; //view
	}

	// 單筆資料：由首頁列表的「詳細」連過來，依 poId 顯示採購單與明細
	@GetMapping("/listOneReceiving")
	public String listOneReceiving(@RequestParam("poId") Integer poId, Model model) {
		PoVO poVO = receivingSvc.getOneApprovedPo(poId);

		// 查不到，或不是申請通過的採購單時回首頁
		if (poVO == null) {
			return "redirect:/admin/psi/receiving";
		}
		model.addAttribute("poVO", poVO);

		return "admin/psi/receiving/listOneReceiving"; //view
	}

}
