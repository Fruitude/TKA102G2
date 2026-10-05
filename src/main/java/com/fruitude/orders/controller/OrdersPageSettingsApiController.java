package com.fruitude.orders.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.orders.model.OrdersPageSettings;

/**
 * 後台訂單管理「每頁筆數」的設定 API，預留給將來的設定頁使用。
 *
 * GET /admin/orders/settings/page-size            → {"pageSize":50,"min":1,"max":200}
 * PUT /admin/orders/settings/page-size  {"pageSize":100} → 同上；超出範圍回 400 與錯誤訊息
 *
 * TODO 目前後台還沒有登入／權限控管，這個 API 任何人都能呼叫；後台上線前要限制為管理員才能使用。
 */
@RestController
@RequestMapping("/admin/orders/settings/page-size")
public class OrdersPageSettingsApiController {

	@Autowired
	private OrdersPageSettings settings;

	@GetMapping
	public Map<String, Integer> get() {
		return body();
	}

	@PutMapping
	public ResponseEntity<?> update(@RequestBody Map<String, Integer> request) {
		Integer pageSize = request == null ? null : request.get("pageSize");
		if (pageSize == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "缺少 pageSize"));
		}
		try {
			settings.setPageSize(pageSize);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
		return ResponseEntity.ok(body());
	}

	private Map<String, Integer> body() {
		return Map.of(
				"pageSize", settings.getPageSize(),
				"min", OrdersPageSettings.MIN_PAGE_SIZE,
				"max", OrdersPageSettings.MAX_PAGE_SIZE);
	}
}
