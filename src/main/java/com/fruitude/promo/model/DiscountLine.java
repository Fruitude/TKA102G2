package com.fruitude.promo.model;

// 結帳時一個品項的價格資料，給商品折扣（全館折扣、壽星月、新會員首購）計算用：
// unitPrice 是前台實際單價（有指定商品活動價就是活動價），originalUnitPrice 是規格原價，qty 是數量
public record DiscountLine(int unitPrice, int originalUnitPrice, int qty) {

	// 這個品項因為活動價少付的金額（沒有活動價就是 0）
	public long promoSaving() {
		return (long) Math.max(0, originalUnitPrice - unitPrice) * qty;
	}

	// 這個品項以前台實際單價計算的金額（畫面上的小計）
	public long lineTotal() {
		return (long) unitPrice * qty;
	}

	// 這個品項以原價計算的金額
	public long originalLineTotal() {
		return (long) originalUnitPrice * qty;
	}
}
