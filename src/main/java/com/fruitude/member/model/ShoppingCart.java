package com.fruitude.member.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 購物車實體：每一個物件對應 shopping_cart 資料表中某會員的一個規格。
 * 只保存規格編號、數量與勾選狀態；價格、名稱、庫存一律在讀取時向商品資料即時查詢。
 */
@Entity
@Table(name = "shopping_cart")
public class ShoppingCart {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "shopping_cart_id")
	private Integer cartId;

	// 用會員編號區分資料擁有者；同一會員同一規格只會有一筆。
	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	@Column(name = "sku_id", nullable = false)
	private Integer skuId;

	@Column(name = "product_amount")
	private Integer qty;

	// 資料庫以 1/0 保存是否勾選結帳。
	@Column(name = "checked", nullable = false, columnDefinition = "TINYINT")
	private Byte checked;

	public Integer getCartId() {
		return cartId;
	}

	public void setCartId(Integer cartId) {
		this.cartId = cartId;
	}

	public Integer getMemberId() {
		return memberId;
	}

	public void setMemberId(Integer memberId) {
		this.memberId = memberId;
	}

	public Integer getSkuId() {
		return skuId;
	}

	public void setSkuId(Integer skuId) {
		this.skuId = skuId;
	}

	public Integer getQty() {
		return qty;
	}

	public void setQty(Integer qty) {
		this.qty = qty;
	}

	public Byte getChecked() {
		return checked;
	}

	public void setChecked(Byte checked) {
		this.checked = checked;
	}

}
