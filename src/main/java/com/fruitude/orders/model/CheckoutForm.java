package com.fruitude.orders.model;

import java.io.Serializable;

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
	
}
