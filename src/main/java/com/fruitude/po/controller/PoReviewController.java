package com.fruitude.po.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.po.model.PoReviewService;
import com.fruitude.po.model.PoVO;

// 採購單審核：會計作業系統的「採購單審核」分頁，和採購單共用 PoVO、PoDetailVO
@Controller
@RequestMapping("/admin/account/purchasereview")
public class PoReviewController {

	@Autowired
	PoReviewService poReviewSvc;

	// 採購單審核首頁：列出待審核的採購單
	@GetMapping("")
	public String purchasereview(Model model) {
		model.addAttribute("poListData", poReviewSvc.getPendingReviewPos());
		return "admin/account/purchasereview/index"; //view
	}

	// 審核頁：由首頁列表的「詳細」連過來，依 poId 顯示採購單與明細
	@GetMapping("/updatePurchaseReview")
	public String updatePurchaseReview(@RequestParam("poId") Integer poId, Model model) {
		PoVO poVO = poReviewSvc.getOnePendingReviewPo(poId);

		// 查不到，或不是待審核的採購單時回首頁
		if (poVO == null) {
			return "redirect:/admin/account/purchasereview";
		}
		model.addAttribute("poVO", poVO);

		return "admin/account/purchasereview/updatePurchaseReview"; //view
	}

}
