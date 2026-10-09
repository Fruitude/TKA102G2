package com.fruitude.promo.model;

// 結帳時套用的商品折扣：折扣金額，以及是哪個活動給的折扣（活動標題，沒有折扣時是空字串）。
// promoProjectId、promoType 是給下單時判斷要不要記錄「每年限用一次」的使用資格用的（沒有折扣時是 null）
public record ProductDiscount(int amount, String title, Integer promoProjectId, String promoType) {

	public static final ProductDiscount NONE = new ProductDiscount(0, "", null, null);
}
