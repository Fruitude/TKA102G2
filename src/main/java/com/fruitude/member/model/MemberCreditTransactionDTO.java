package com.fruitude.member.model;

import java.time.LocalDateTime;

/**
 * 購物金異動資料傳輸物件：整合流水、會員姓名帳號與經手員工姓名，供後台集中查詢顯示。
 */
public class MemberCreditTransactionDTO {

	private Integer transactionId;
	private Integer memberId;
	private String memberName;
	private String memberAccount;
	private Byte transactionType;
	private Integer amount;
	private Integer balanceBefore;
	private Integer balanceAfter;
	private Integer ordersId;
	private Integer refundOrderId;
	private Integer employeeId;
	private String employeeName;
	private String reason;
	private LocalDateTime createdAt;

	public MemberCreditTransactionDTO(Integer transactionId, Integer memberId, String memberName,
			String memberAccount, Byte transactionType, Integer amount, Integer balanceBefore,
			Integer balanceAfter, Integer ordersId, Integer refundOrderId, Integer employeeId,
			String employeeName, String reason, LocalDateTime createdAt) {
		this.transactionId = transactionId;
		this.memberId = memberId;
		this.memberName = memberName;
		this.memberAccount = memberAccount;
		this.transactionType = transactionType;
		this.amount = amount;
		this.balanceBefore = balanceBefore;
		this.balanceAfter = balanceAfter;
		this.ordersId = ordersId;
		this.refundOrderId = refundOrderId;
		this.employeeId = employeeId;
		this.employeeName = employeeName;
		this.reason = reason;
		this.createdAt = createdAt;
	}

	public Integer getTransactionId() { return transactionId; }
	public Integer getMemberId() { return memberId; }
	public String getMemberName() { return memberName; }
	public String getMemberAccount() { return memberAccount; }
	public Byte getTransactionType() { return transactionType; }
	public Integer getAmount() { return amount; }
	public Integer getBalanceBefore() { return balanceBefore; }
	public Integer getBalanceAfter() { return balanceAfter; }
	public Integer getOrdersId() { return ordersId; }
	public Integer getRefundOrderId() { return refundOrderId; }
	public Integer getEmployeeId() { return employeeId; }
	public String getEmployeeName() { return employeeName; }
	public String getReason() { return reason; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
