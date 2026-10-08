package com.fruitude.promo.model;

import java.util.ArrayList;
import java.util.List;

// 活動類型，存進 promo_project.promo_type 的是 name()。
// 每個類型同時決定後台表單要填哪些欄位：可選的優惠方式、要不要優惠值、最低消費、名額（以及是否必填）。
// 後台畫面和伺服器檢查都讀這裡，新增或調整類型只要改這一處。
public enum PromoType {
	//             顯示文字        可選的優惠方式                                       優惠值  最低消費(必填)   名額(必填)
	STOREWIDE("全館折扣",           List.of(BenefitType.PERCENT_OFF, BenefitType.AMOUNT_OFF), true,  false, false, false, false),
	SKU("指定商品",                 List.of(BenefitType.FIXED_PRICE),                          false, false, false, false, false),
	BIRTHDAY_MONTH("壽星月",        List.of(BenefitType.PERCENT_OFF, BenefitType.AMOUNT_OFF), true,  false, false, false, false),
	NEW_MEMBER_FIRST_ORDER("新會員首購", List.of(BenefitType.PERCENT_OFF, BenefitType.AMOUNT_OFF), true, true, false, false, false),
	FREE_SHIPPING("滿額免運",        List.of(BenefitType.FREE_SHIPPING),                        false, true,  true,  false, false),
	WALLET_GRAB("搶購物金",         List.of(BenefitType.WALLET_CREDIT),                        true,  false, false, true,  true),
	REVIEW_REWARD("評論送購物金",    List.of(BenefitType.WALLET_CREDIT),                        true,  false, false, false, false);

	private final String label;
	private final List<BenefitType> benefitTypes;   // 只有一個選項時，後台不顯示優惠方式，伺服器自動帶入
	private final boolean usesBenefitValue;         // 指定商品的價格在 promotion 表，免運沒有數值，所以不需要
	private final boolean usesMinOrderAmount;
	private final boolean minOrderAmountRequired;
	private final boolean usesQuota;
	private final boolean quotaRequired;

	PromoType(String label, List<BenefitType> benefitTypes, boolean usesBenefitValue, boolean usesMinOrderAmount,
			boolean minOrderAmountRequired, boolean usesQuota, boolean quotaRequired) {
		this.label = label;
		this.benefitTypes = benefitTypes;
		this.usesBenefitValue = usesBenefitValue;
		this.usesMinOrderAmount = usesMinOrderAmount;
		this.minOrderAmountRequired = minOrderAmountRequired;
		this.usesQuota = usesQuota;
		this.quotaRequired = quotaRequired;
	}

	public String getLabel() {
		return label;
	}

	public List<BenefitType> getBenefitTypes() {
		return benefitTypes;
	}

	public boolean isUsesBenefitValue() {
		return usesBenefitValue;
	}

	public boolean isUsesMinOrderAmount() {
		return usesMinOrderAmount;
	}

	public boolean isMinOrderAmountRequired() {
		return minOrderAmountRequired;
	}

	public boolean isUsesQuota() {
		return usesQuota;
	}

	public boolean isQuotaRequired() {
		return quotaRequired;
	}

	// 常態福利：沒有固定檔期、長期提供給符合資格的會員（首頁放在「會員專屬福利」，不顯示倒數）。
	// 其餘類型是有明確起訖的限時活動
	public boolean isRecurring() {
		return this == BIRTHDAY_MONTH || this == NEW_MEMBER_FIRST_ORDER || this == REVIEW_REWARD;
	}

	// 首頁是否顯示：評論送購物金、搶購物金還沒有發放購物金的功能，先不宣傳，做好之後從這裡拿掉
	public boolean isShownOnFront() {
		return this != REVIEW_REWARD && this != WALLET_GRAB;
	}

	// 代碼轉活動類型；空值或不認識的代碼回傳 null
	public static PromoType of(String code) {
		for (PromoType t : values()) {
			if (t.name().equals(code)) {
				return t;
			}
		}
		return null;
	}

	// 代碼是否為合法的活動類型
	public static boolean isValid(String code) {
		return of(code) != null;
	}

	// 這個類型實際要存的優惠方式：只有一種選擇就直接用它；有多種就要使用者選的那個，選的不在清單內回傳 null
	public String resolveBenefitType(String selected) {
		if (benefitTypes.size() == 1) {
			return benefitTypes.get(0).name();
		}
		for (BenefitType b : benefitTypes) {
			if (b.name().equals(selected)) {
				return b.name();
			}
		}
		return null;
	}

	// 代碼轉顯示文字；空值或不認識的代碼顯示「未設定」
	public static String labelOf(String code) {
		PromoType t = of(code);
		return t == null ? "未設定" : t.label;
	}

	// 給後台表單 JS 用的規則，格式：{"FREE_SHIPPING":{"benefitTypes":["FREE_SHIPPING"],"usesValue":false,...},...}。
	// 內容只有英文代碼與布林值，直接組字串不會有跳脫問題
	public static String rulesJson() {
		StringBuilder sb = new StringBuilder("{");
		PromoType[] all = values();
		for (int i = 0; i < all.length; i++) {
			PromoType t = all[i];
			List<String> codes = new ArrayList<>();
			for (BenefitType b : t.benefitTypes) {
				codes.add("\"" + b.name() + "\"");
			}
			sb.append("\"").append(t.name()).append("\":{")
					.append("\"benefitTypes\":[").append(String.join(",", codes)).append("],")
					.append("\"usesValue\":").append(t.usesBenefitValue).append(",")
					.append("\"usesMin\":").append(t.usesMinOrderAmount).append(",")
					.append("\"minRequired\":").append(t.minOrderAmountRequired).append(",")
					.append("\"usesQuota\":").append(t.usesQuota).append(",")
					.append("\"quotaRequired\":").append(t.quotaRequired).append("}");
			if (i < all.length - 1) {
				sb.append(",");
			}
		}
		return sb.append("}").toString();
	}
}
