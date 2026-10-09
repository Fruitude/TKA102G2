package com.fruitude.promo.model;

import java.time.LocalDateTime;
import java.util.List;

// 前台活動資料：限時活動（有倒數）與會員專屬福利（常態，不顯示倒數）。首頁與活動總覽頁共用
public record HomePromos(List<Item> limited, List<Item> perks) {

	// benefit 是優惠內容（例如「9 折」）；condition 是使用資格（例如「首次下單」「滿 1000 元」），可能是空字串；
	// start、end 是活動期間；highlight 是這位會員目前正符合資格，highlightText 是要顯示的標籤文字（例如「本月適用」「首購適用」）；used 是今年已經用掉了（壽星月每年限用一次）。
	// context（活動說明）、quota（名額，null 代表不限）、products（指定商品活動的商品）只有活動總覽頁才會填，首頁是 null / 空清單
	public record Item(Integer promoProjectId, String title, String typeLabel, String benefit, String condition,
			LocalDateTime start, LocalDateTime end, boolean highlight, String highlightText, boolean used, String context, Integer quota,
			List<Product> products) {
	}

	// 指定商品活動裡的一個商品規格：原價與活動價
	public record Product(Integer productId, String productName, String skuName, Integer originalPrice,
			Integer promoPrice) {
	}
}
