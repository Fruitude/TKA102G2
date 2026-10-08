package com.fruitude.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 結帳流程（/front/checkout/**）一定要先登入會員：訂單、購物金、壽星月等優惠都是以登入的會員為準。
 * 沒登入時：
 * - 結帳頁與確認頁、送出結帳資料：導到登入頁，登入後回到結帳頁（?next=）
 * - 前端用 fetch 呼叫的 API（下單、查折扣）：回 401，由頁面自己處理
 * 免運門檻與郵遞區號清單是公開資訊，不用登入。
 */
public class CheckoutLoginInterceptor implements HandlerInterceptor {

	private static final String LOGIN_PATH = "/front/about/login/";
	private static final String CHECKOUT_PATH = "/front/checkout/";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String contextPath = request.getContextPath();
		String path = request.getRequestURI().substring(contextPath.length());

		if (path.equals(CHECKOUT_PATH + "free-shipping") || path.equals(CHECKOUT_PATH + "postal-codes")) {
			return true;
		}
		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute("loggedInMemberId") instanceof Integer) {
			return true;
		}

		// 給 fetch 用的 API：不能導頁，回 401 讓前端處理
		if (path.equals(CHECKOUT_PATH + "place-order") || path.equals(CHECKOUT_PATH + "product-discount")
				|| path.equals(CHECKOUT_PATH + "credit-balance") || path.equals(CHECKOUT_PATH + "member-defaults")
				|| path.equals(CHECKOUT_PATH + "birthday-promo")) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("text/plain;charset=UTF-8");
			response.getWriter().write("請先登入會員");
			return false;
		}

		// 頁面：只有 GET 的頁面登入後可以原路回來；POST（送出結帳資料）資料已經丟了，回到結帳頁重填
		String next = "GET".equals(request.getMethod()) ? path : CHECKOUT_PATH;
		response.sendRedirect(contextPath + LOGIN_PATH + "?next=" + URLEncoder.encode(contextPath + next, StandardCharsets.UTF_8));
		return false;
	}
}
