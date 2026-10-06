package com.fruitude.promo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 活動商品（某個活動中，某個商品規格的活動價），對應資料表 promotion
@Entity
@Table(name = "promotion")
public class Promotion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "promotion_id")
	private Integer promotionId;

	@Column(name = "promo_project_id")
	private Integer promoProjectId;

	@Column(name = "sku_id")
	private Integer skuId;

	@Column(name = "promo_price")
	private Integer promoPrice;

	public Integer getPromotionId() {
		return promotionId;
	}

	public void setPromotionId(Integer promotionId) {
		this.promotionId = promotionId;
	}

	public Integer getPromoProjectId() {
		return promoProjectId;
	}

	public void setPromoProjectId(Integer promoProjectId) {
		this.promoProjectId = promoProjectId;
	}

	public Integer getSkuId() {
		return skuId;
	}

	public void setSkuId(Integer skuId) {
		this.skuId = skuId;
	}

	public Integer getPromoPrice() {
		return promoPrice;
	}

	public void setPromoPrice(Integer promoPrice) {
		this.promoPrice = promoPrice;
	}
}
