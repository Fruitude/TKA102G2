package com.fruitude.po.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
	// 審核完也會回到這一頁顯示結果，所以不限待審核的採購單；審核的按鈕由頁面以 poVO.editable 判斷要不要顯示
	@GetMapping("/updatePurchaseReview")
	public String updatePurchaseReview(@RequestParam("poId") Integer poId, Model model,
			RedirectAttributes redirectAttributes) {
		PoVO poVO = poReviewSvc.getOnePo(poId);

		// 查不到採購單時回首頁
		if (poVO == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "查無此採購單");
			return "redirect:/admin/account/purchasereview";
		}
		model.addAttribute("poVO", poVO);
		// 審核者這次看到的內容摘要，隨表單送回 approve，用來擋下審核頁開著的期間被修改過的採購單
		model.addAttribute("reviewedContent", poReviewSvc.getReviewedContent(poVO));

		return "admin/account/purchasereview/updatePurchaseReview"; //view
	}

	// 審核通過：採購單狀態改為申請通過，並把採購數量加到規格的待進貨
	// reviewedContent 是審核頁顯示當時的內容摘要；沒有帶或和資料庫當下的內容不一樣時不通過
	@PostMapping("/approve")
	public String approve(@RequestParam("poId") Integer poId,
			@RequestParam(value = "reviewedContent", required = false) String reviewedContent,
			RedirectAttributes redirectAttributes) {
		try {
			poReviewSvc.approve(poId, reviewedContent);
			redirectAttributes.addFlashAttribute("success", "審核成功");
		} catch (IllegalArgumentException e) {
			// 無法通過的原因（已經審核過、內容被修改過、沒有明細、供應商已停用、規格已永久停產）；整筆已回滾，回審核頁顯示最新的內容
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return redirectToUpdatePurchaseReview(poId);
	}

	// 審核不通過：採購單狀態改為申請未通過
	@PostMapping("/reject")
	public String reject(@RequestParam("poId") Integer poId, RedirectAttributes redirectAttributes) {
		try {
			poReviewSvc.reject(poId);
			redirectAttributes.addFlashAttribute("success", "已審核為不通過");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return redirectToUpdatePurchaseReview(poId);
	}

	// 取消採購：採購單狀態改為已取消
	@PostMapping("/cancel")
	public String cancel(@RequestParam("poId") Integer poId, RedirectAttributes redirectAttributes) {
		try {
			poReviewSvc.cancel(poId);
			redirectAttributes.addFlashAttribute("success", "已取消採購");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return redirectToUpdatePurchaseReview(poId);
	}

	// 回審核頁的網址
	private String redirectToUpdatePurchaseReview(Integer poId) {
		return "redirect:/admin/account/purchasereview/updatePurchaseReview?poId=" + poId;
	}

}
