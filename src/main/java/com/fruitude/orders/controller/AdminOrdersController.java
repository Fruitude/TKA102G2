package com.fruitude.orders.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.orders.model.OrdersPageSettings;
import com.fruitude.orders.model.OrdersService;

import jakarta.persistence.Tuple;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrdersController {
	@Autowired
	private OrdersService ordersService;

	// 每頁筆數不寫死在這裡，由 OrdersPageSettings 提供（可透過 OrdersPageSettingsApiController 修改）
	@Autowired
	private OrdersPageSettings pageSettings;

	@GetMapping("/")
	public String getOrderList(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "id") String field,
			@RequestParam(defaultValue = "") String keyword,
			Model model) {
		// 每次請求只讀一次，避免處理途中被 API 改掉造成頁碼算錯
		int pageSize = pageSettings.getPageSize();
		// page 從 1 開始給使用者看；超出範圍就夾回第一頁到最後一頁
		Page<Tuple> result = ordersService.search(field, keyword, Math.max(page, 1) - 1, pageSize);
		if (result.getTotalPages() > 0 && page > result.getTotalPages()) {
			result = ordersService.search(field, keyword, result.getTotalPages() - 1, pageSize);
		}
		// 搜尋條件要傳回頁面：填回輸入框，翻頁連結也要帶著它
		model.addAttribute("field", field);
		model.addAttribute("keyword", keyword.trim());
		int current = result.getNumber() + 1;
		long total = result.getTotalElements();
		model.addAttribute("ordersList", result.getContent());
		model.addAttribute("currentPage", current);
		model.addAttribute("totalPages", result.getTotalPages());
		model.addAttribute("totalCount", total);
		model.addAttribute("fromIndex", total == 0 ? 0 : (long) result.getNumber() * pageSize + 1);
		model.addAttribute("toIndex", (long) result.getNumber() * pageSize + result.getNumberOfElements());
		return "admin/orders/index";
	}
	
	@GetMapping("/orderDetail")
	public String getOrderDetail(@RequestParam Integer ordersId, Model model) {
		List<Tuple> dataList = ordersService.getOrderDetailById(ordersId);
		model.addAttribute("ordersDetailList", dataList);
		return "admin/orders/detail/index";
	}
}
