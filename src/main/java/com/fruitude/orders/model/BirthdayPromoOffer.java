package com.fruitude.orders.model;

// 結帳確認頁要不要顯示「使用壽星優惠」勾選框：eligible 為 true 才顯示，title 是活動標題，benefit 是優惠內容（例如「7 折」）
public record BirthdayPromoOffer(boolean eligible, String title, String benefit) {

	public static final BirthdayPromoOffer NONE = new BirthdayPromoOffer(false, "", "");
}
