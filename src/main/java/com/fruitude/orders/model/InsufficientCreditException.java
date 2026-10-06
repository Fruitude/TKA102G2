package com.fruitude.orders.model;

// 結帳時要折抵的購物金超過會員的餘額
public class InsufficientCreditException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InsufficientCreditException(String message) {
		super(message);
	}
}
