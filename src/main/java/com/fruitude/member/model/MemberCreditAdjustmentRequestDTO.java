package com.fruitude.member.model;

import java.time.LocalDateTime;

/** 後台購物金調整申請列表資料，含會員與申請／審核員工姓名。 */
public class MemberCreditAdjustmentRequestDTO {

	private Integer creditAdjustmentRequestId;
	private Integer memberId;
	private String memberName;
	private String memberAccount;
	private Byte adjustmentType;
	private Integer amount;
	private Integer balanceAtRequest;
	private String reasonCategory;
	private String reasonDetail;
	private Byte requestStatus;
	private Integer requestedByEmployeeId;
	private String requestedByEmployeeName;
	private LocalDateTime requestedAt;
	private Integer reviewedByEmployeeId;
	private String reviewedByEmployeeName;
	private LocalDateTime reviewedAt;
	private String reviewNote;
	private Integer creditTransactionId;

	public MemberCreditAdjustmentRequestDTO(Integer creditAdjustmentRequestId, Integer memberId, String memberName,
			String memberAccount, Byte adjustmentType, Integer amount, Integer balanceAtRequest,
			String reasonCategory, String reasonDetail, Byte requestStatus, Integer requestedByEmployeeId,
			String requestedByEmployeeName, LocalDateTime requestedAt, Integer reviewedByEmployeeId,
			String reviewedByEmployeeName, LocalDateTime reviewedAt, String reviewNote,
			Integer creditTransactionId) {
		this.creditAdjustmentRequestId = creditAdjustmentRequestId;
		this.memberId = memberId;
		this.memberName = memberName;
		this.memberAccount = memberAccount;
		this.adjustmentType = adjustmentType;
		this.amount = amount;
		this.balanceAtRequest = balanceAtRequest;
		this.reasonCategory = reasonCategory;
		this.reasonDetail = reasonDetail;
		this.requestStatus = requestStatus;
		this.requestedByEmployeeId = requestedByEmployeeId;
		this.requestedByEmployeeName = requestedByEmployeeName;
		this.requestedAt = requestedAt;
		this.reviewedByEmployeeId = reviewedByEmployeeId;
		this.reviewedByEmployeeName = reviewedByEmployeeName;
		this.reviewedAt = reviewedAt;
		this.reviewNote = reviewNote;
		this.creditTransactionId = creditTransactionId;
	}

	public Integer getCreditAdjustmentRequestId() { return creditAdjustmentRequestId; }
	public Integer getMemberId() { return memberId; }
	public String getMemberName() { return memberName; }
	public String getMemberAccount() { return memberAccount; }
	public Byte getAdjustmentType() { return adjustmentType; }
	public Integer getAmount() { return amount; }
	public Integer getBalanceAtRequest() { return balanceAtRequest; }
	public String getReasonCategory() { return reasonCategory; }
	public String getReasonDetail() { return reasonDetail; }
	public Byte getRequestStatus() { return requestStatus; }
	public Integer getRequestedByEmployeeId() { return requestedByEmployeeId; }
	public String getRequestedByEmployeeName() { return requestedByEmployeeName; }
	public LocalDateTime getRequestedAt() { return requestedAt; }
	public Integer getReviewedByEmployeeId() { return reviewedByEmployeeId; }
	public String getReviewedByEmployeeName() { return reviewedByEmployeeName; }
	public LocalDateTime getReviewedAt() { return reviewedAt; }
	public String getReviewNote() { return reviewNote; }
	public Integer getCreditTransactionId() { return creditTransactionId; }
}
