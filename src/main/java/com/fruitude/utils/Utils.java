package com.fruitude.utils;

public class Utils {
	public static String getOrderStatus(OrderStatus orderStatus, PaymentStatus paymentStatus) {
	    return orderStatus.getExpectedPaymentStatus() == paymentStatus
	            ? orderStatus.getDisplayStatus()
	            : "狀態異常";
	}
	
	public enum OrderStatus {

	    PREPARING_SHIPMENT(0, "準備出貨", "待出貨", PaymentStatus.PAID, MemberTab.PENDING_SHIPMENT),
	    SHIPPED(1, "出貨按鈕", "配送中", PaymentStatus.PAID, MemberTab.PENDING_RECEIPT),
	    CANCELLED_NON_CUSTOMER(2, "非客戶端問題取消訂單", "訂單取消", PaymentStatus.REFUND_PENDING, MemberTab.REFUND),
	    DELIVERY_FAILED_NON_CUSTOMER(3, "非客戶端問題配送失敗", "配送中", PaymentStatus.PAID, MemberTab.PENDING_RECEIPT),
	    CANCELLED_BY_CUSTOMER(4, "客戶端主動取消訂單", "訂單取消", PaymentStatus.REFUND_PENDING, MemberTab.REFUND),
	    DELIVERY_FAILED_CUSTOMER(5, "客戶端問題配送失敗", "訂單失效", PaymentStatus.PAID, MemberTab.CANCELED),
	    REFUND_REJECTED_BY_ACCOUNTING(6, "退款申請會計審核不通過", "申訴未通過訂單已完成", PaymentStatus.PAID, MemberTab.COMPLETED),
	    DELIVERED(7, "客戶已收件", "已配送", PaymentStatus.PAID, MemberTab.COMPLETED),
	    ACCEPTED(8, "客戶驗收成功", "訂單已完成", PaymentStatus.PAID, MemberTab.COMPLETED),
	    COMPLAINT_FILED(9, "客戶收件後提出申訴", "客服處理中", PaymentStatus.PAID, MemberTab.REFUND),
	    REFUND_PENDING_ACCOUNTING(10, "可以退款送會計", "客服處理中", PaymentStatus.PAID, MemberTab.REFUND),
	    REFUND_APPROVED(11, "會計審核通過", "退款作業中", PaymentStatus.REFUND_PENDING, MemberTab.REFUND),
	    REFUNDED(12, "金額已退還給客戶", "訂單已完成", PaymentStatus.REFUNDED, MemberTab.COMPLETED);

	    private final int code;
	    private final String description;
	    private final String displayStatus;
	    private final PaymentStatus expectedPaymentStatus;
	    private final MemberTab memberTab;

	    OrderStatus(int code, String description, String displayStatus, PaymentStatus expectedPaymentStatus,
	            MemberTab memberTab) {
	        this.code = code;
	        this.description = description;
	        this.displayStatus = displayStatus;
	        this.expectedPaymentStatus = expectedPaymentStatus;
	        this.memberTab = memberTab;
	    }

	    public int getCode() { return code; }
	    public String getDescription() { return description; }
	    public String getDisplayStatus() { return displayStatus; }
	    public PaymentStatus getExpectedPaymentStatus() { return expectedPaymentStatus; }
	    public MemberTab getMemberTab() { return memberTab; }

	    public static OrderStatus fromCode(int code) {
	        for (OrderStatus status : values()) {
	            if (status.code == code) {
	                return status;
	            }
	        }
	        return null;
	    }
	}
	
	public enum Vendorstatus{
		
		INACTIVE(0,"尚未啟用"),
		ACTIVE(1,"已啟用"),
		STOP(2,"已停用");
		
		private final int code;
	    private final String description;

	    Vendorstatus(int code, String description) {
	        this.code = code;
	        this.description = description;
	    }

	    public int getCode() { return code; }

	    public String getDescription() { return description; }

	    public static Vendorstatus fromCode(int code) {
	        for (Vendorstatus status : values()) {
	            if (status.code == code) {
	                return status;
	            }
	        }
	        throw new IllegalArgumentException("Unknown vendor status code: " + code);
	    }
	}
	
	// 會員「購買清單」頁的分頁：由訂單出貨狀態碼與金流狀態碼合併後決定（對照表見 OrderStatus）。
	public enum MemberTab {

	    PENDING_SHIPMENT("pending-shipment", "待出貨"),
	    PENDING_RECEIPT("pending-receipt", "待收貨"),
	    COMPLETED("completed", "訂單已完成"),
	    REFUND("refund", "退貨/退款"),
	    CANCELED("canceled", "不成立");

	    private final String key;
	    private final String label;

	    MemberTab(String key, String label) {
	        this.key = key;
	        this.label = label;
	    }

	    public String getKey() { return key; }
	    public String getLabel() { return label; }
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
