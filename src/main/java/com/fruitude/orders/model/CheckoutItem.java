package com.fruitude.orders.model;

import java.io.Serializable;

/**
 * 結帳頁送出時，一併夾帶的購物車品項（來自 localStorage）。
 * 欄位名稱要跟 cart.js 送出的 JSON key 一樣，Jackson 才能自動對應。
 * 這裡的 price、qty 只是顯示、暫存用；下單時金額一定要用資料庫目前的價格重新計算，
 * 不能直接信任這裡的值（使用者可以在瀏覽器裡任意竄改 localStorage）。
 */
public class CheckoutItem implements Serializable {

	private static final long serialVersionUID = 1L;

	// 對應資料庫欄位 sku_id（Integer）。商品還沒接資料庫，先用 1~3 的暫定值，
	// 之後接上真正的商品資料時，換成該商品實際的 sku_id 即可，欄位本身不用再改名
	private Integer skuId;

	// 對應資料庫欄位 product_name
	private String productName;

	private Integer price;
	private Integer qty;

	public Integer getSkuId() {
		return skuId;
	}
	public void setSkuId(Integer skuId) {
		this.skuId = skuId;
	}
	public String getProductName() {
		return productName;
	}
	public void setProductName(String productName) {
		this.productName = productName;
	}
	public Integer getPrice() {
		return price;
	}
	public void setPrice(Integer price) {
		this.price = price;
	}
	// 規格原價。由伺服器在 validateCheckoutItems 時依資料庫填入（price 低於它就是活動價），不採用前端送來的值
	private Integer originalPrice;

	public Integer getOriginalPrice() {
		return originalPrice;
	}
	public void setOriginalPrice(Integer originalPrice) {
		this.originalPrice = originalPrice;
	}
	public Integer getQty() {
		return qty;
	}
	public void setQty(Integer qty) {
		this.qty = qty;
	}
}
