package com.fruitude.promo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fruitude.orders.model.OrdersService;
import com.fruitude.promo.model.HomePromos;
import com.fruitude.promo.model.PromoService;

import jakarta.servlet.http.HttpSession;

/** 前台活動總覽頁：進行中的限時活動與會員專屬福利，含完整說明、使用條件與指定商品 */
@Controller
public class FrontPromoController {

	private final PromoService promoService;
	private final OrdersService ordersService;

	public FrontPromoController(PromoService promoService, OrdersService ordersService) {
		this.promoService = promoService;
		this.ordersService = ordersService;
	}

	@GetMapping("/front/promotions")
	public String canonical() {
		return "redirect:/front/promotions/";
	}

	@GetMapping({ "/front/promotions/", "/front/promotions/index", "/front/promotions/index.html" })
	public String promotions(Model model, HttpSession session) {
		// 會員身分只信任 session，用來標示目前符合資格的福利（壽星月）（與首頁一致）
		Integer memberId = session.getAttribute("loggedInMemberId") instanceof Integer id ? id : null;
		boolean loggedIn = memberId != null;
		HomePromos promos = promoService.findPromotionsPage(loggedIn,
				loggedIn && ordersService.isBirthdayMonth(memberId),
                loggedIn && ordersService.isBirthdayPromoUsed(memberId));
		model.addAttribute("limitedPromos", promos.limited());
		model.addAttribute("memberPerks", promos.perks());
		return "front/promotions/index";
	}
}
