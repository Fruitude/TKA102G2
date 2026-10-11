package com.fruitude.shipping.model;

import com.fruitude.orders.model.OrdersDetail;
import com.fruitude.product.model.ProductSku;

// 出貨頁面用的一筆明細：訂單明細加上它的商品規格
// OrdersDetail 只存規格編號，規格名稱、庫存要另外從 ProductSku 取得，所以把兩個放在一起交給頁面
public class ShippingDetail {

	private OrdersDetail ordersDetail;

	// 規格編號查不到規格時是 null
	private ProductSku productSku;

	public ShippingDetail(OrdersDetail ordersDetail, ProductSku productSku) {
		this.ordersDetail = ordersDetail;
		this.productSku = productSku;
	}

	public OrdersDetail getOrdersDetail() {
		return ordersDetail;
	}

	public ProductSku getProductSku() {
		return productSku;
	}

	// 目前庫存夠不夠出這一筆：庫存量 >= 訂購數量；查不到規格時當作不夠
	public boolean isStockEnough() {
		if (productSku == null) {
			return false;
		}
		int stock = productSku.getStock() == null ? 0 : productSku.getStock();
		int ordersQuantity = ordersDetail.getOrdersQuantity() == null ? 0 : ordersDetail.getOrdersQuantity();
		return stock >= ordersQuantity;
	}

}
