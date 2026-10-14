package com.fruitude.config;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fruitude.employee.model.EmployeeAdminAccessService;
import com.fruitude.employee.model.EmployeeAdminAccessService.AccessProfile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 後台統一門禁：先驗證員工登入，再依員工現有權限限制功能頁面與資料 API。
 */
@Component
public class AdminAccessInterceptor implements HandlerInterceptor {

	private final EmployeeAdminAccessService accessService;

	public AdminAccessInterceptor(EmployeeAdminAccessService accessService) {
		this.accessService = accessService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String path = applicationPath(request);
		if (isPublicPath(path, request.getMethod())) return true;

		HttpSession session = request.getSession(false);
		Integer employeeId = sessionEmployeeId(session);
		Optional<AccessProfile> profileResult = accessService.findAccessProfile(employeeId);
		if (profileResult.isEmpty()) {
			if (session != null) session.invalidate();
			return rejectUnauthenticated(request, response, path);
		}

		AccessProfile profile = profileResult.get();
		// Thymeleaf 共用側邊欄使用這兩個屬性，只顯示目前員工能開啟的功能。
		request.setAttribute("adminPermissionCodes", profile.getPermissionCodes());
		request.setAttribute("adminIsSystemAdministrator", profile.isAdministrator());

		Set<String> required = requiredPermissions(path, request.getMethod());
		if (!required.isEmpty() && Collections.disjoint(profile.getPermissionCodes(), required)) {
			return rejectForbidden(request, response);
		}
		return true;
	}

	private boolean rejectUnauthenticated(HttpServletRequest request, HttpServletResponse response, String path)
			throws IOException {
		if (isApiPath(path)) {
			writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "請先登入後台員工帳號");
			return false;
		}
		String destination = path;
		if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
			destination += "?" + request.getQueryString();
		}
		String next = URLEncoder.encode(destination, StandardCharsets.UTF_8.name());
		response.sendRedirect(request.getContextPath() + "/admin/employees/login?next=" + next);
		return false;
	}

	private boolean rejectForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (isApiPath(applicationPath(request))) {
			writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "你的職位沒有此功能權限");
			return false;
		}
		response.sendRedirect(request.getContextPath() + "/admin/access-denied");
		return false;
	}

	private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"" + message + "\"}");
	}

	private Integer sessionEmployeeId(HttpSession session) {
		if (session == null) return null;
		Object value = session.getAttribute("loggedInEmployeeId");
		return value instanceof Number ? ((Number) value).intValue() : null;
	}

	private String applicationPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
	}

	private boolean isApiPath(String path) {
		return path.startsWith("/api/admin/");
	}

	private boolean isPublicPath(String path, String method) {
		// 前台商品卡片共用這個圖片讀取網址；只有純數字圖片編號的 GET 可以公開。
		if ("GET".equalsIgnoreCase(method) && path.matches("/product/image/\\d+(?:/thumbnail)?")) return true;
		if (path.equals("/admin/employees/login") || path.equals("/admin/employees/apply")
				|| path.equals("/admin/employees/reset-password")
				|| path.equals("/admin/access-denied")) return true;
		if (path.equals("/api/admin/session") && "GET".equalsIgnoreCase(method)) return true;
		// 員工必須持有信件中的一次性 Token；驗證與送出新密碼不要求先登入。
		if (path.equals("/api/admin/employees/password/reset/validate")
				&& "GET".equalsIgnoreCase(method)) return true;
		if (path.equals("/api/admin/employees/password/reset")
				&& "POST".equalsIgnoreCase(method)) return true;
		return path.equals("/api/admin/session/login")
				|| path.equals("/api/admin/session/applications") && "POST".equalsIgnoreCase(method);
	}

	/**
	 * 將後台網址對應到既有權限代碼；同一集合中的任一權限符合即可進入該功能。
	 */
	static Set<String> requiredPermissions(String path, String method) {
		boolean write = !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);

		if (path.startsWith("/api/admin/members")) {
			if (path.contains("/credit") && write) return permissions("MEMBER_CREDIT");
			if (path.endsWith("/status") && write) return permissions("MEMBER_STATUS");
			return permissions("MEMBER_VIEW");
		}
		if (path.startsWith("/api/admin/comments")) {
			// 評論檢舉處理與客服聊天室是不同功能，分別使用評論查看及處理權限。
			return write ? permissions("COMMENT_MODERATE") : permissions("COMMENT_VIEW");
		}
		if (path.startsWith("/api/admin/employees")) {
			if (path.contains("/permissions") || path.contains("/positions")
					|| path.contains("/permission-functions")) return permissions("PERMISSION_MANAGE");
			if (path.endsWith("/password-reset-email") && write) return permissions("EMPLOYEE_EDIT");
			if (path.endsWith("/status") && write) return permissions("EMPLOYEE_STATUS");
			if (path.endsWith("/password") && write) return permissions("EMPLOYEE_EDIT");
			if (write && path.matches("/api/admin/employees/?")) return permissions("EMPLOYEE_CREATE");
			if (write) return permissions("EMPLOYEE_EDIT", "EMPLOYEE_STATUS");
			return permissions("EMPLOYEE_VIEW", "PERMISSION_MANAGE");
		}

		if (path.startsWith("/admin/employees")) return permissions("EMPLOYEE_VIEW", "PERMISSION_MANAGE");
		if (path.startsWith("/admin/members")) return permissions("MEMBER_VIEW");
		if (path.startsWith("/admin/comments")) return permissions("COMMENT_VIEW");
		if (path.startsWith("/admin/orders")) return write ? permissions("ORDER_EDIT") : permissions("ORDER_VIEW");
		if (path.startsWith("/admin/promo")) {
			if (!write) return permissions("PROMOTION_VIEW");
			return path.endsWith("/create") ? permissions("PROMOTION_CREATE") : permissions("PROMOTION_EDIT");
		}
		// 小農頁面必須先於通用資訊路由判斷，避免誤用 FAQ 或最新消息權限。
		if (path.startsWith("/admin/info/farmers")) {
			return write ? permissions("FARMER_MANAGE") : permissions("FARMER_VIEW");
		}
		if (path.startsWith("/admin/info")) return permissions("FAQ_MANAGE", "NEWS_MANAGE");
		// 庫存查詢是獨立功能，必須先於通用商品與進銷存路由判斷。
		if (path.startsWith("/admin/product/stock") || path.startsWith("/admin/psi/stock")) {
			return permissions("INVENTORY_VIEW");
		}
		if (path.startsWith("/admin/product")) return permissions("PRODUCT_VIEW");
		if (path.startsWith("/admin/psi/vendor")) return permissions("SUPPLIER_MANAGE");
		if (path.startsWith("/admin/psi/receiving")) return permissions("PURCHASE_RECEIVE");
		if (path.startsWith("/admin/psi/shipping")) {
			return write ? permissions("SHIPPING_PROCESS") : permissions("SHIPPING_VIEW");
		}
		if (path.startsWith("/admin/psi/purchase")) {
			if (!write) return permissions("PURCHASE_VIEW");
			return path.endsWith("/insert") ? permissions("PURCHASE_CREATE") : permissions("PURCHASE_EDIT");
		}
		if (path.startsWith("/admin/psi")) {
			return permissions("PRODUCT_VIEW", "PURCHASE_VIEW", "PURCHASE_RECEIVE", "SUPPLIER_MANAGE", "ORDER_EDIT");
		}
		if (path.startsWith("/admin/account/refundreview")) {
			return write ? permissions("REFUND_PROCESS") : permissions("REFUND_VIEW");
		}
		if (path.startsWith("/admin/account/purchasereview")) {
			return write ? permissions("PURCHASE_EDIT") : permissions("PURCHASE_VIEW");
		}
		if (path.startsWith("/admin/account")) return permissions("REFUND_VIEW", "PURCHASE_VIEW");

		// 商品同學的舊路由不在 /admin 底下，也要套用相同後台門禁。
		if (path.startsWith("/product/image")) return permissions("PRODUCT_IMAGE");
		if (path.startsWith("/productcategory")) return permissions("PRODUCT_CATEGORY");
		if (path.startsWith("/productsku")) return permissions("PRODUCT_SPEC");
		if (path.startsWith("/productimage")) return permissions("PRODUCT_IMAGE");
		if (path.startsWith("/product")) {
			if (!write) return permissions("PRODUCT_VIEW");
			return path.contains("insert") ? permissions("PRODUCT_CREATE") : permissions("PRODUCT_EDIT");
		}
		return Collections.emptySet();
	}

	private static Set<String> permissions(String... codes) {
		Set<String> result = new LinkedHashSet<>();
		Collections.addAll(result, codes);
		return result;
	}
}
