package com.fruitude.orders.model;

// 結帳頁勾選「收件者同會員」時帶入的資料：會員姓名、Email、預設電話、預設地址，
// 以及由地址算出的 3 碼郵遞區號。沒有的欄位是 null（例如會員沒有設定預設地址，或地址認不出區號）
public record MemberCheckoutDefaults(String name, String email, String phone, String address, String postalCode) {

	public static final MemberCheckoutDefaults EMPTY = new MemberCheckoutDefaults(null, null, null, null, null);
}
