package com.fruitude.promo.model;

// 優惠方式，存進 promo_project.benefit_type 的是 name()
public enum BenefitType {
	PERCENT_OFF("折扣"),
	AMOUNT_OFF("折抵金額"),
	FIXED_PRICE("固定價格"),
	WALLET_CREDIT("贈送購物金"),
	FREE_SHIPPING("免運費");

	private final String label;

	BenefitType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	// 代碼是否為合法的優惠方式
	public static boolean isValid(String code) {
		for (BenefitType t : values()) {
			if (t.name().equals(code)) {
				return true;
			}
		}
		return false;
	}

	// 把優惠方式加優惠值轉成人看得懂的文字，給列表的「優惠內容」欄用：
	// 折扣 90 → 「9 折」、85 → 「8.5 折」；折抵 100 → 「折抵 100 元」；贈送購物金 500 → 「贈送 500 點」
	public static String describe(String code, Integer value) {
		if (FREE_SHIPPING.name().equals(code)) {
			return "免運費";
		}
		if (FIXED_PRICE.name().equals(code)) {
			return "依活動商品價格";
		}
		if (value == null) {
			return "";
		}
		if (PERCENT_OFF.name().equals(code)) {
			return value % 10 == 0 ? (value / 10) + " 折" : (value / 10.0) + " 折";
		}
		if (AMOUNT_OFF.name().equals(code)) {
			return "折抵 " + value + " 元";
		}
		if (WALLET_CREDIT.name().equals(code)) {
			return "贈送 " + value + " 點";
		}
		return String.valueOf(value);
	}

	// 代碼轉顯示文字；空值或不認識的代碼顯示「未設定」
	public static String labelOf(String code) {
		for (BenefitType t : values()) {
			if (t.name().equals(code)) {
				return t.label;
			}
		}
		return "未設定";
	}
}
