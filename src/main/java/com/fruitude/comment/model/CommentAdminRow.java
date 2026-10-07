package com.fruitude.comment.model;

import java.time.LocalDateTime;

/**
 * 後台評論清單資料：整合訂單明細、訂單與會員資料，但不修改其他模組的 Entity。
 */
public class CommentAdminRow {

	private final Integer ordersDetailId;
	private final Integer ordersId;
	private final Integer skuId;
	private final String productName;
	private final Integer memberId;
	private final String memberName;
	private final String memberAccount;
	private final String commentText;
	private final LocalDateTime commentDate;
	private final Integer commentStatus;
	private final Integer commentStar;

	public CommentAdminRow(Integer ordersDetailId, Integer ordersId, Integer skuId, String productName,
			Integer memberId, String memberName, String memberAccount, String commentText,
			LocalDateTime commentDate, Integer commentStatus, Integer commentStar) {
		this.ordersDetailId = ordersDetailId;
		this.ordersId = ordersId;
		this.skuId = skuId;
		this.productName = productName;
		this.memberId = memberId;
		this.memberName = memberName;
		this.memberAccount = memberAccount;
		this.commentText = commentText;
		this.commentDate = commentDate;
		this.commentStatus = commentStatus;
		this.commentStar = commentStar;
	}

	public Integer getOrdersDetailId() { return ordersDetailId; }
	public Integer getOrdersId() { return ordersId; }
	public Integer getSkuId() { return skuId; }
	public String getProductName() { return productName; }
	public Integer getMemberId() { return memberId; }
	public String getMemberName() { return memberName; }
	public String getMemberAccount() { return memberAccount; }
	public String getCommentText() { return commentText; }
	public LocalDateTime getCommentDate() { return commentDate; }
	public Integer getCommentStatus() { return commentStatus; }
	public Integer getCommentStar() { return commentStar; }
}
