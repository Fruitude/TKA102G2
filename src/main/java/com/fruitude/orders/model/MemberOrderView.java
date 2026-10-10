package com.fruitude.orders.model;

import java.time.LocalDateTime;
import java.util.List;

// 會員「購買清單」頁的一筆訂單：tab 是所屬分頁的 key（例如 pending-shipment），
// status 是合併出貨狀態與金流狀態後給會員看的文字，amount 是訂單金額（實付金額），
// canComment 表示這張訂單目前可以評論（已配送、已驗收）
public record MemberOrderView(Integer ordersId, LocalDateTime ordersDate, String tab, String status,
		Integer amount, boolean canComment, List<Item> items) {

	// 訂單內的一項商品：品名與數量；reviewed 表示這位會員已經評論過這個商品（本單或其他訂單）
	public record Item(Integer ordersDetailId, Integer skuId, String productName, Integer quantity,
			boolean reviewed) {
	}
}
