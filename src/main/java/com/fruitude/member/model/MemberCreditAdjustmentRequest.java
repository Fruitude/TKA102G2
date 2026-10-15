package com.fruitude.member.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 購物金調整申請：員工只能送單，核准後才會異動會員餘額與流水。 */
@Entity
@Table(name = "member_credit_adjustment_request")
public class MemberCreditAdjustmentRequest {

	public static final byte TYPE_ADD = 1;
	public static final byte TYPE_DEDUCT = 2;

	public static final byte STATUS_PENDING = 0;
	public static final byte STATUS_APPROVED = 1;
	public static final byte STATUS_REJECTED = 2;
	public static final byte STATUS_CANCELLED = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "credit_adjustment_request_id")
	private Integer creditAdjustmentRequestId;

	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	@Column(name = "adjustment_type", nullable = false, columnDefinition = "TINYINT")
	private Byte adjustmentType;

	@Column(name = "amount", nullable = false)
	private Integer amount;

	@Column(name = "balance_at_request", nullable = false)
	private Integer balanceAtRequest;

	@Column(name = "reason_category", length = 30, nullable = false)
	private String reasonCategory;

	@Column(name = "reason_detail", length = 255, nullable = false)
	private String reasonDetail;

	@Column(name = "request_status", nullable = false, columnDefinition = "TINYINT")
	private Byte requestStatus;

	@Column(name = "requested_by_employee_id", nullable = false)
	private Integer requestedByEmployeeId;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private LocalDateTime requestedAt;

	@Column(name = "reviewed_by_employee_id")
	private Integer reviewedByEmployeeId;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "review_note", length = 255)
	private String reviewNote;

	@Column(name = "credit_transaction_id", unique = true)
	private Integer creditTransactionId;

	@PrePersist
	public void applyDefaults() {
		if (requestStatus == null) requestStatus = STATUS_PENDING;
		if (requestedAt == null) requestedAt = LocalDateTime.now();
	}

	public Integer getCreditAdjustmentRequestId() { return creditAdjustmentRequestId; }
	public void setCreditAdjustmentRequestId(Integer creditAdjustmentRequestId) {
		this.creditAdjustmentRequestId = creditAdjustmentRequestId;
	}
	public Integer getMemberId() { return memberId; }
	public void setMemberId(Integer memberId) { this.memberId = memberId; }
	public Byte getAdjustmentType() { return adjustmentType; }
	public void setAdjustmentType(Byte adjustmentType) { this.adjustmentType = adjustmentType; }
	public Integer getAmount() { return amount; }
	public void setAmount(Integer amount) { this.amount = amount; }
	public Integer getBalanceAtRequest() { return balanceAtRequest; }
	public void setBalanceAtRequest(Integer balanceAtRequest) { this.balanceAtRequest = balanceAtRequest; }
	public String getReasonCategory() { return reasonCategory; }
	public void setReasonCategory(String reasonCategory) { this.reasonCategory = reasonCategory; }
	public String getReasonDetail() { return reasonDetail; }
	public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
	public Byte getRequestStatus() { return requestStatus; }
	public void setRequestStatus(Byte requestStatus) { this.requestStatus = requestStatus; }
	public Integer getRequestedByEmployeeId() { return requestedByEmployeeId; }
	public void setRequestedByEmployeeId(Integer requestedByEmployeeId) {
		this.requestedByEmployeeId = requestedByEmployeeId;
	}
	public LocalDateTime getRequestedAt() { return requestedAt; }
	public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
	public Integer getReviewedByEmployeeId() { return reviewedByEmployeeId; }
	public void setReviewedByEmployeeId(Integer reviewedByEmployeeId) {
		this.reviewedByEmployeeId = reviewedByEmployeeId;
	}
	public LocalDateTime getReviewedAt() { return reviewedAt; }
	public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
	public String getReviewNote() { return reviewNote; }
	public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
	public Integer getCreditTransactionId() { return creditTransactionId; }
	public void setCreditTransactionId(Integer creditTransactionId) {
		this.creditTransactionId = creditTransactionId;
	}
}
