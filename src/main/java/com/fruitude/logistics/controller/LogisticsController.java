package com.fruitude.logistics.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.logistics.model.LogisticsService;
import com.fruitude.orders.model.Orders;
import com.fruitude.utils.PsiUtils;

@Controller
@RequestMapping("/admin/logistics/delivery")
public class LogisticsController {

	@Autowired
	LogisticsService logisticsSvc;

	// 配送系統：列出配送中的訂單，每頁 10 筆；page 是頁碼，從 1 開始，網址沒帶就是第 1 頁
	@GetMapping("")
	public String delivery(@RequestParam(value = "page", defaultValue = "1") int page, Model model) {
		Page<Orders> ordersPage = logisticsSvc.getDeliveringOrders(page);
		model.addAttribute("ordersPage", ordersPage);
		// 頁碼清單，0 代表「…」；ordersPage.number 從 0 開始，buildPageNumbers 從 1 開始
		model.addAttribute("pageNumbers", PsiUtils.buildPageNumbers(ordersPage.getNumber() + 1, ordersPage.getTotalPages()));
		return "admin/logistics/delivery/index"; //view
	}

	// 配送：由列表的「配送」按鈕送過來，ordersIds 是勾選的訂單編號；成功或被擋下都 redirect 回列表顯示訊息
	@PostMapping("/deliver")
	public String deliver(@RequestParam(value = "ordersIds", required = false) List<Integer> ordersIds,
			RedirectAttributes redirectAttributes) {
		try {
			int count = logisticsSvc.deliverOrders(ordersIds);
			redirectAttributes.addFlashAttribute("success", "已配送 " + count + " 筆訂單");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/logistics/delivery";
	}
}
