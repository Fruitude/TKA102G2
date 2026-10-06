package com.fruitude.member.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 會員購物金流水實體：每次增加或扣除都保存前後餘額，不能只覆蓋會員餘額。 */
@Entity
@Table(name = "member_credit_transaction", indexes = {
		@Index(name = "idx_credit_member_created", columnList = "member_id,created_at")
})
public class MemberCreditTransaction {

	public static final byte TYPE_REFUND = 1;
	public static final byte TYPE_ORDER_USE = 2;
	public static final byte TYPE_ADMIN_ADD = 3;
	public static final byte TYPE_ADMIN_DEDUCT = 4;
	public static final byte TYPE_ORDER_CANCEL_RETURN = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "credit_transaction_id")
	private Integer creditTransactionId;

	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	@Column(name = "transaction_type", nullable = false, columnDefinition = "TINYINT")
	private Byte transactionType;

	@Column(name = "amount", nullable = false)
	private Integer amount;

	@Column(name = "balance_before", nullable = false)
	private Integer balanceBefore;

	@Column(name = "balance_after", nullable = false)
	private Integer balanceAfter;

	@Column(name = "orders_id")
	private Integer ordersId;

	// 同一張退款單只能入帳一次，避免重複操作造成會員購物金重複增加。
	@Column(name = "refundorder_id", unique = true)
	private Integer refundOrderId;

	@Column(name = "employee_id")
	private Integer employeeId;

	@Column(name = "reason", length = 255)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** 新增流水時自動保存伺服器時間。 */
	@PrePersist
	public void applyCreatedAt() { if (createdAt == null) createdAt = LocalDateTime.now(); }

	public Integer getCreditTransactionId() { return creditTransactionId; }
	public void setCreditTransactionId(Integer value) { this.creditTransactionId = value; }
	public Integer getMemberId() { return memberId; }
	public void setMemberId(Integer memberId) { this.memberId = memberId; }
	public Byte getTransactionType() { return transactionType; }
	public void setTransactionType(Byte transactionType) { this.transactionType = transactionType; }
	public Integer getAmount() { return amount; }
	public void setAmount(Integer amount) { this.amount = amount; }
	public Integer getBalanceBefore() { return balanceBefore; }
	public void setBalanceBefore(Integer balanceBefore) { this.balanceBefore = balanceBefore; }
	public Integer getBalanceAfter() { return balanceAfter; }
	public void setBalanceAfter(Integer balanceAfter) { this.balanceAfter = balanceAfter; }
	public Integer getOrdersId() { return ordersId; }
	public void setOrdersId(Integer ordersId) { this.ordersId = ordersId; }
	public Integer getRefundOrderId() { return refundOrderId; }
	public void setRefundOrderId(Integer refundOrderId) { this.refundOrderId = refundOrderId; }
	public Integer getEmployeeId() { return employeeId; }
	public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
