package com.fruitude.chat.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 聊天訊息（message 表）：一個會員一段對話，以 member_id 區分
// 只對應欄位、不建關聯；employee_id 只存在資料庫給內部查詢，不會送到頁面（頁面用 ChatMessageView）
@Entity
@Table(name = "message")
public class Message {

	// sender_type：0 系統、1 會員、2 員工
	public static final int SENDER_SYSTEM = 0;
	public static final int SENDER_MEMBER = 1;
	public static final int SENDER_EMPLOYEE = 2;

	// message_type：0 文字、1 圖片（可同時有文字）、2 罐頭訊息（客服由建議回覆帶入，送出前可修改）
	public static final int TYPE_TEXT = 0;
	public static final int TYPE_IMAGE = 1;
	public static final int TYPE_CANNED = 2;

	// is_read：0 未讀、1 已讀
	public static final int UNREAD = 0;
	public static final int READ = 1;

	// status：0 隱藏（只對會員隱藏，後台仍看得到）、1 顯示
	public static final int STATUS_HIDDEN = 0;
	public static final int STATUS_SHOWN = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "message_id")
	private Integer messageId;

	@Column(name = "sender_type", nullable = false)
	private Integer senderType;

	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	@Column(name = "employee_id")
	private Integer employeeId;

	@Column(name = "message_type", nullable = false)
	private Integer messageType;

	@Column(name = "message_content", columnDefinition = "TEXT")
	private String messageContent;

	@Column(name = "reply_message_id")
	private Integer replyMessageId;

	@Column(name = "is_read", nullable = false)
	private Integer isRead;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "status", nullable = false)
	private Integer status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public Integer getMessageId() {
		return messageId;
	}

	public void setMessageId(Integer messageId) {
		this.messageId = messageId;
	}

	public Integer getSenderType() {
		return senderType;
	}

	public void setSenderType(Integer senderType) {
		this.senderType = senderType;
	}

	public Integer getMemberId() {
		return memberId;
	}

	public void setMemberId(Integer memberId) {
		this.memberId = memberId;
	}

	public Integer getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(Integer employeeId) {
		this.employeeId = employeeId;
	}

	public Integer getMessageType() {
		return messageType;
	}

	public void setMessageType(Integer messageType) {
		this.messageType = messageType;
	}

	public String getMessageContent() {
		return messageContent;
	}

	public void setMessageContent(String messageContent) {
		this.messageContent = messageContent;
	}

	public Integer getReplyMessageId() {
		return replyMessageId;
	}

	public void setReplyMessageId(Integer replyMessageId) {
		this.replyMessageId = replyMessageId;
	}

	public Integer getIsRead() {
		return isRead;
	}

	public void setIsRead(Integer isRead) {
		this.isRead = isRead;
	}

	public LocalDateTime getReadAt() {
		return readAt;
	}

	public void setReadAt(LocalDateTime readAt) {
		this.readAt = readAt;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

}
