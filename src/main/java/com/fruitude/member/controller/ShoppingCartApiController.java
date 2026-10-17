package com.fruitude.member.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.ShoppingCartService;
import com.fruitude.member.model.ShoppingCartService.CartInput;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 登入會員的購物車 API。所有操作都以 session 會員編號為準，不能指定別人的會員編號。
 */
@RestController
@RequestMapping("/front/api/cart")
public class ShoppingCartApiController {

	private final ShoppingCartService shoppingCartService;

	public ShoppingCartApiController(ShoppingCartService shoppingCartService) {
		this.shoppingCartService = shoppingCartService;
	}

	/** 取得目前登入會員的購物車。 */
	@GetMapping
	public ResponseEntity<?> getCart(HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(shoppingCartService.findCart(memberId));
	}

	/** 以送來的完整清單取代目前登入會員的購物車，並回傳儲存後的內容。 */
	@PutMapping
	public ResponseEntity<?> replaceCart(@RequestBody List<CartInput> items, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(shoppingCartService.replaceCart(memberId, items));
	}

	/** 清空目前登入會員的購物車。 */
	@DeleteMapping
	public ResponseEntity<?> clearCart(HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		if (memberId == null) {
			return unauthorized();
		}
		shoppingCartService.clear(memberId);
		return ResponseEntity.noContent().build();
	}

	// 登入時 MemberController 已把會員編號放入 session，這裡只讀取該值。
	private Integer getLoggedInMemberId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute("loggedInMemberId") instanceof Integer)) {
			return null;
		}
		return (Integer) session.getAttribute("loggedInMemberId");
	}

	private ResponseEntity<Map<String, String>> unauthorized() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", "請先登入會員");
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}
}
