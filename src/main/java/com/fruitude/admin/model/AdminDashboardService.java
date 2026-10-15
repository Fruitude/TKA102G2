package com.fruitude.admin.model;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.product.model.ProductSku;

@Service
public class AdminDashboardService {

	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
	private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
	private static final String UNKNOWN_VENDOR = "未指定供應商";
	private static final String OTHER_VENDOR = "其他";

	@Autowired
	private AdminDashboardRepository repository;

	// 近 months 個月（從當天 00:00 往前推）銷售量前 limit 名的 SKU
	@Transactional(readOnly = true)
	public List<TopSellingSku> findTopSellingSkus(int months, int limit) {
		LocalDateTime now = now();
		var rows = repository.findTopSellingSkus(periodStart(now, months), now, limit);

		List<TopSellingSku> result = new ArrayList<>();
		int rank = 1;
		for (var row : rows) {
			result.add(new TopSellingSku(rank++, row.getSkuId(), row.getProductName(),
					ProductSku.resolveDisplayName(row.getSkuName(), row.getAnotherName()),
					toLong(row.getTotalQuantity()), toLong(row.getTotalAmount())));
		}
		return result;
	}

	// 每月銷售數量與金額；從起始月到本月逐月列出，沒有銷售的月份補 0，讓圖表 X 軸連續
	@Transactional(readOnly = true)
	public List<MonthlySales> findMonthlySales(int months) {
		LocalDateTime now = now();
		LocalDateTime fromDate = periodStart(now, months);
		Map<String, AdminDashboardRepository.MonthlySalesRow> rowsByMonth = new HashMap<>();
		for (var row : repository.findMonthlySales(fromDate, now)) {
			rowsByMonth.put(row.getMonth(), row);
		}

		List<MonthlySales> result = new ArrayList<>();
		YearMonth endMonth = YearMonth.from(now);
		for (YearMonth month = YearMonth.from(fromDate); !month.isAfter(endMonth); month = month.plusMonths(1)) {
			String key = month.format(MONTH_FORMAT);
			var row = rowsByMonth.get(key);
			result.add(row == null ? new MonthlySales(key, 0L, 0L)
					: new MonthlySales(key, toLong(row.getTotalQuantity()), toLong(row.getTotalAmount())));
		}
		return result;
	}

	// 銷售金額前 limit 名的供應商；總金額扣掉前幾名後還有剩，就多加一筆「其他」
	@Transactional(readOnly = true)
	public List<VendorSales> findTopVendorSales(int months, int limit) {
		LocalDateTime now = now();
		LocalDateTime fromDate = periodStart(now, months);

		List<VendorSales> result = new ArrayList<>();
		long topAmount = 0L;
		for (var row : repository.findTopVendorSales(fromDate, now, limit)) {
			long amount = toLong(row.getTotalAmount());
			String vendorName = row.getVendorName() == null ? UNKNOWN_VENDOR : row.getVendorName();
			result.add(new VendorSales(row.getVendorId(), vendorName, amount, false));
			topAmount += amount;
		}

		long otherAmount = toLong(repository.findTotalSalesAmount(fromDate, now)) - topAmount;
		if (otherAmount > 0) {
			result.add(new VendorSales(null, OTHER_VENDOR, otherAmount, true));
		}
		return result;
	}

	private static LocalDateTime now() {
		return LocalDateTime.now(TAIPEI);
	}

	private static LocalDateTime periodStart(LocalDateTime now, int months) {
		return now.toLocalDate().minusMonths(months).atStartOfDay();
	}

	private static long toLong(Number value) {
		return value == null ? 0L : value.longValue();
	}

	public record TopSellingSku(int rank, Integer skuId, String productName, String skuName,
			long totalQuantity, long totalAmount) {
	}

	public record MonthlySales(String month, long totalQuantity, long totalAmount) {
	}

	public record VendorSales(Integer vendorId, String vendorName, long totalAmount, boolean other) {
	}
}
