package com.fruitude.chat.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fruitude.chat.model.FaqLiveAnswer;
import com.fruitude.chat.model.FaqService;

import jakarta.servlet.http.HttpServletRequest;

// 後台 FAQ 管理：頁面與頁面用的 API，維護客服聊天室自動回覆（CannedReplyService.faqReply）用的 FAQ
// 查看與修改都要 CHAT_REPLY；/admin/chat 不在 AdminAccessInterceptor 的權限規則裡，所以和 AdminChatController 一樣在這裡自己檢查
@Controller
public class AdminFaqController {

	@Autowired
	private FaqService faqSvc;

	// FAQ 管理頁面：沒有 CHAT_REPLY 時導到沒有權限的頁面
	// liveQuestions 是即時回覆的題目，用 | 串成一個字串放進頁面的 data 屬性，
	// faq-admin.js 用來把這幾題的刪除鈕變灰、問題欄位設成唯讀
	@GetMapping("/admin/chat/faq")
	public String faq(HttpServletRequest request, Model model) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return "redirect:/admin/access-denied";
		}
		model.addAttribute("liveQuestions", String.join("|", FaqLiveAnswer.LIVE_QUESTIONS));
		return "admin/chat/faq"; //view
	}

	// 全部類別，依編號排序
	@GetMapping("/api/admin/chat/faq/categories")
	@ResponseBody
	public ResponseEntity<?> categories(HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(faqSvc.getCategories());
	}

	@PostMapping("/api/admin/chat/faq/categories")
	@ResponseBody
	public ResponseEntity<?> addCategory(@RequestBody FaqService.CategoryForm form, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok(faqSvc.addCategory(form));
	}

	@PostMapping("/api/admin/chat/faq/categories/{faqCategoryId}")
	@ResponseBody
	public ResponseEntity<?> updateCategory(@PathVariable Integer faqCategoryId,
			@RequestBody FaqService.CategoryForm form, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok(faqSvc.updateCategory(faqCategoryId, form));
	}

	// 類別啟用（enabled=true）或停用（enabled=false）
	@PostMapping("/api/admin/chat/faq/categories/{faqCategoryId}/status")
	@ResponseBody
	public ResponseEntity<?> categoryStatus(@PathVariable Integer faqCategoryId,
			@RequestParam("enabled") boolean enabled, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok(faqSvc.setCategoryStatus(faqCategoryId, enabled));
	}

	// 刪除類別：只能刪除空的類別，有子類別或 FAQ 時由 FaqService 丟錯，badRequest 回 400 與原因
	@PostMapping("/api/admin/chat/faq/categories/{faqCategoryId}/delete")
	@ResponseBody
	public ResponseEntity<?> deleteCategory(@PathVariable Integer faqCategoryId, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		faqSvc.deleteCategory(faqCategoryId);
		return ResponseEntity.ok(Map.of());
	}

	// 拖拉排序：body 是這個類別拖拉後的 FAQ 編號順序
	@PostMapping("/api/admin/chat/faq/categories/{faqCategoryId}/order")
	@ResponseBody
	public ResponseEntity<?> reorder(@PathVariable Integer faqCategoryId, @RequestBody List<Integer> faqIds,
			HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		faqSvc.reorderFaqs(faqCategoryId, faqIds);
		return ResponseEntity.ok(Map.of());
	}

	// 全部 FAQ，依類別、顯示順序排序；篩選與搜尋由頁面自己做
	@GetMapping("/api/admin/chat/faq/items")
	@ResponseBody
	public ResponseEntity<?> faqs(HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(faqSvc.getFaqs());
	}

	@PostMapping("/api/admin/chat/faq/items")
	@ResponseBody
	public ResponseEntity<?> addFaq(@RequestBody FaqService.FaqForm form, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok(faqSvc.addFaq(form));
	}

	@PostMapping("/api/admin/chat/faq/items/{faqId}")
	@ResponseBody
	public ResponseEntity<?> updateFaq(@PathVariable Integer faqId, @RequestBody FaqService.FaqForm form,
			HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok(faqSvc.updateFaq(faqId, form));
	}

	// 刪除 FAQ：即時回覆的題目由 FaqService 丟錯，badRequest 回 400 與原因
	@PostMapping("/api/admin/chat/faq/items/{faqId}/delete")
	@ResponseBody
	public ResponseEntity<?> deleteFaq(@PathVariable Integer faqId, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		faqSvc.deleteFaq(faqId);
		return ResponseEntity.ok(Map.of());
	}

	// 必填空白、太長、名稱重複、上層類別形成迴圈等，回 400 並帶原因，頁面顯示在彈出視窗裡
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseBody
	public ResponseEntity<?> badRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
	}

	// 目前員工有沒有這個權限；adminPermissionCodes 由 AdminAccessInterceptor 放入，沒有時當作沒有權限
	private boolean hasPermission(HttpServletRequest request, String permissionCode) {
		return request.getAttribute("adminPermissionCodes") instanceof Set<?> permissionCodes
				&& permissionCodes.contains(permissionCode);
	}

	private ResponseEntity<?> forbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "你的職位沒有此功能權限"));
	}

}
