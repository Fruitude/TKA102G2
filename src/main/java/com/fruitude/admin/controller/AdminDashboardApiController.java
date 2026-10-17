package com.fruitude.admin.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.admin.model.AdminDashboardService;
import com.fruitude.admin.model.AdminDashboardService.MonthlySales;
import com.fruitude.admin.model.AdminDashboardService.TopSellingSku;
import com.fruitude.admin.model.AdminDashboardService.VendorSales;

/**
 * 後台首頁統計 API。
 *
 * GET /api/admin/dashboard/top-selling-skus?months=3&limit=10 → 近 N 個月 SKU 銷售量排行
 * GET /api/admin/dashboard/monthly-sales?months=3              → 每月銷售數量與金額
 * GET /api/admin/dashboard/top-vendor-sales?months=3&limit=3   → 供應商銷售金額排行（含「其他」）
 * 登入檢查由 AdminAccessInterceptor 處理（/api/admin/**，未登入回 401）。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardApiController {

	private static final int MAX_MONTHS = 12;
	private static final int MAX_LIMIT = 20;

	@Autowired
	private AdminDashboardService dashboardService;

	@GetMapping("/top-selling-skus")
	public List<TopSellingSku> topSellingSkus(
			@RequestParam(defaultValue = "3") int months,
			@RequestParam(defaultValue = "10") int limit) {
		return dashboardService.findTopSellingSkus(safeMonths(months), safeLimit(limit));
	}

	@GetMapping("/monthly-sales")
	public List<MonthlySales> monthlySales(@RequestParam(defaultValue = "3") int months) {
		return dashboardService.findMonthlySales(safeMonths(months));
	}

	@GetMapping("/top-vendor-sales")
	public List<VendorSales> topVendorSales(
			@RequestParam(defaultValue = "3") int months,
			@RequestParam(defaultValue = "3") int limit) {
		return dashboardService.findTopVendorSales(safeMonths(months), safeLimit(limit));
	}

	private static int safeMonths(int months) {
		return Math.min(Math.max(months, 1), MAX_MONTHS);
	}

	private static int safeLimit(int limit) {
		return Math.min(Math.max(limit, 1), MAX_LIMIT);
	}
}
