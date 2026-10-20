package com.fruitude.orders.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.orders.model.OrdersService;

import jakarta.servlet.http.HttpSession;

/** 登入會員的購買清單 API。會員編號一律取自登入 session，不接受前端傳 */
@RestController
@RequestMapping("/api/members/me/orders")
public class MemberOrdersApiController {

	@Autowired
	private OrdersService ordersService;

	/** 目前登入會員的所有訂單（新的在前），每筆含所屬分頁、狀態、商品品名與數量、訂單金額 */
	@GetMapping
	public ResponseEntity<?> myOrders(HttpSession session) {
		if (!(session.getAttribute("loggedInMemberId") instanceof Integer memberId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "請先登入會員"));
		}
		return ResponseEntity.ok(ordersService.findMemberOrders(memberId));
	}

	/** 收貨：登入會員把自己已配送（7）的訂單改成客戶驗收成功（8）；不是他的訂單或狀態不符一律回 409 */
	@PostMapping("/{ordersId}/receive")
	public ResponseEntity<?> receive(@PathVariable Integer ordersId, HttpSession session) {
		if (!(session.getAttribute("loggedInMemberId") instanceof Integer memberId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "請先登入會員"));
		}
		if (!ordersService.receiveOrders(memberId, ordersId)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "這張訂單目前不能完成"));
		}
		return ResponseEntity.ok(Map.of("message", "訂單已完成"));
	}
}
