package com.fruitude.utils;

public class Utils {
	public static String getOrderStatus(OrderStatus orderStatus, PaymentStatus paymentStatus) {
	    return orderStatus.getExpectedPaymentStatus() == paymentStatus
	            ? orderStatus.getDisplayStatus()
	            : "狀態異常";
	}
	
	public enum OrderStatus {

	    PREPARING_SHIPMENT(0, "準備出貨", "待出貨", PaymentStatus.PAID),
	    SHIPPED(1, "出貨按鈕", "配送中", PaymentStatus.PAID),
	    CANCELLED_NON_CUSTOMER(2, "非客戶端問題取消訂單", "訂單取消", PaymentStatus.REFUND_PENDING),
	    DELIVERY_FAILED_NON_CUSTOMER(3, "非客戶端問題配送失敗", "配送中", PaymentStatus.PAID),
	    CANCELLED_BY_CUSTOMER(4, "客戶端主動取消訂單", "訂單取消", PaymentStatus.REFUND_PENDING),
	    DELIVERY_FAILED_CUSTOMER(5, "客戶端問題配送失敗", "訂單失效", PaymentStatus.PAID),
	    REFUND_REJECTED_BY_ACCOUNTING(6, "退款申請會計審核不通過", "申訴未通過訂單已完成", PaymentStatus.PAID),
	    DELIVERED(7, "客戶已收件", "已配送", PaymentStatus.PAID),
	    ACCEPTED(8, "客戶驗收成功", "訂單已完成", PaymentStatus.PAID),
	    COMPLAINT_FILED(9, "客戶收件後提出申訴", "客服處理中", PaymentStatus.PAID),
	    REFUND_PENDING_ACCOUNTING(10, "可以退款送會計", "客服處理中", PaymentStatus.PAID),
	    REFUND_APPROVED(11, "會計審核通過", "退款作業中", PaymentStatus.REFUND_PENDING),
	    REFUNDED(12, "金額已退還給客戶", "訂單已完成", PaymentStatus.REFUNDED);

	    private final int code;
	    private final String description;
	    private final String displayStatus;
	    private final PaymentStatus expectedPaymentStatus;

	    OrderStatus(int code, String description, String displayStatus, PaymentStatus expectedPaymentStatus) {
	        this.code = code;
	        this.description = description;
	        this.displayStatus = displayStatus;
	        this.expectedPaymentStatus = expectedPaymentStatus;
	    }

	    public int getCode() { return code; }
	    public String getDescription() { return description; }
	    public String getDisplayStatus() { return displayStatus; }
	    public PaymentStatus getExpectedPaymentStatus() { return expectedPaymentStatus; }
	}
	
	public enum PaymentStatus {

	    PAID(0, "已付款"),
	    REFUND_PENDING(1, "待退款"),
	    REFUNDED(2, "已退款");

	    private final int code;
	    private final String description;

	    PaymentStatus(int code, String description) {
	        this.code = code;
	        this.description = description;
	    }

	    public int getCode() {
	        return code;
	    }

	    public String getDescription() {
	        return description;
	    }

	    public static PaymentStatus fromCode(int code) {
	        for (PaymentStatus status : values()) {
	            if (status.code == code) {
	                return status;
	            }
	        }
	        throw new IllegalArgumentException("Unknown payment status code: " + code);
	    }
	}
}
