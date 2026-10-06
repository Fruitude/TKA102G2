package com.fruitude.promo.model;

// 結帳時套用的商品折扣：折扣金額，以及是哪個活動給的折扣（活動標題，沒有折扣時是空字串）
public record ProductDiscount(int amount, String title) {

	public static final ProductDiscount NONE = new ProductDiscount(0, "");
}
