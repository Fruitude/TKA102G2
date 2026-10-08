package com.fruitude.orders.model;

import java.time.LocalDateTime;
import java.util.List;

// 會員「購買清單」頁的一筆訂單：tab 是所屬分頁的 key（例如 pending-shipment），
// status 是合併出貨狀態與金流狀態後給會員看的文字，amount 是訂單金額（實付金額）
public record MemberOrderView(Integer ordersId, LocalDateTime ordersDate, String tab, String status,
		Integer amount, List<Item> items) {

	// 訂單內的一項商品：品名與數量
	public record Item(Integer skuId, String productName, Integer quantity) {
	}
}
