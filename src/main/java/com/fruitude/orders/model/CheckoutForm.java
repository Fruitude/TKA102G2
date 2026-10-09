package com.fruitude.orders.model;

import java.io.Serializable;
import java.util.List;

public class CheckoutForm implements Serializable{

	private static final long serialVersionUID = 1L;

    // 屬性名稱要跟表單 input 的 name 一樣，@ModelAttribute 才會自動填入
    private String receiverName;
    private String email;
    private String phoneNumber;
    private String shippingZip;
    private String shippingAddress;
    private String logisticsNote;
    private String paymentMethod;
    private String invoiceCarrier;
    private String orderNote;

    // 結帳頁勾選「使用壽星優惠」（每年限用一次）。表單的 checkbox 只有勾選時才會送出 true，沒勾就保持 false。
    // 這裡只是會員的意願，有沒有資格、有沒有真的套用，下單時由伺服器重新判斷
    private boolean useBirthday;

    // 結帳頁送出時，由 JS 把購物車勾選的商品塞進這個隱藏欄位（JSON 字串），
    // name="cartItemsJson"，跟其他欄位一樣由 @ModelAttribute 自動繫結
    private String cartItemsJson;

    // 不是表單欄位，@ModelAttribute 不會自動填。CheckoutController 收到表單後，
    // 自己把上面的 cartItemsJson 解析成這個清單，再一起存進 session
    private List<CheckoutItem> items;

	public String getReceiverName() {
		return receiverName;
	}
	public void setReceiverName(String receiverName) {
		this.receiverName = receiverName;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPhoneNumber() {
		return phoneNumber;
	}
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
	public String getShippingZip() {
		return shippingZip;
	}
	public void setShippingZip(String shippingZip) {
		this.shippingZip = shippingZip;
	}
	public String getShippingAddress() {
		return shippingAddress;
	}
	public void setShippingAddress(String shippingAddress) {
		this.shippingAddress = shippingAddress;
	}
	public String getPaymentMethod() {
		return paymentMethod;
	}
	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	public String getInvoiceCarrier() {
		return invoiceCarrier;
	}
	public void setInvoiceCarrier(String invoiceCarrier) {
		this.invoiceCarrier = invoiceCarrier;
	}
	public boolean isUseBirthday() {
		return useBirthday;
	}

	public void setUseBirthday(boolean useBirthday) {
		this.useBirthday = useBirthday;
	}

	public String getOrderNote() {
		return orderNote;
	}
	public void setOrderNote(String orderNote) {
		this.orderNote = orderNote;
	}
	public String getLogisticsNote() {
		return logisticsNote;
	}
	public void setLogisticsNote(String logisticsNote) {
		this.logisticsNote = logisticsNote;
	}
	public String getCartItemsJson() {
		return cartItemsJson;
	}
	public void setCartItemsJson(String cartItemsJson) {
		this.cartItemsJson = cartItemsJson;
	}
	public List<CheckoutItem> getItems() {
		return items;
	}
	public void setItems(List<CheckoutItem> items) {
		this.items = items;
	}

}
