package com.fruitude.orders.model;

// 結帳時勾選了「每年限用一次」的優惠（例如壽星優惠），但今年已經用過了
public class PromoAlreadyUsedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PromoAlreadyUsedException(String message) {
		super(message);
	}
}
