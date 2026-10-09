package com.fruitude.promo.model;

// 登入會員目前和「個人化活動」有關的狀態，首頁與活動總覽頁用它決定常態福利要顯示「本月適用／首購適用」或「已使用」：
// - birthdayMonth／birthdayUsed：現在是不是生日月、今年有沒有用掉壽星優惠（每年限用一次）
// - firstPurchaseUsed：新會員首購有沒有用掉（member_promo_usage 有這位會員的首購紀錄；訂單取消或退款時紀錄會刪除，資格就回來）
// 這些都由後端依登入 session 的會員算出來，不接受前端傳入；訪客用 ANONYMOUS
public record MemberPromoState(boolean loggedIn, boolean birthdayMonth, boolean birthdayUsed,
		boolean firstPurchaseUsed) {

	public static final MemberPromoState ANONYMOUS = new MemberPromoState(false, false, false, false);
}
