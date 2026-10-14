package com.fruitude.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 罐頭訊息（canned_message 表）：客服常用的回覆內容
// keyword 是逗號分隔的關鍵字，CannedReplyService 用它比對會員的訊息，找出建議回覆
// 只對應欄位、不建關聯；所屬類別（canned_category）是否顯示由 CannedMessageRepository 的查詢判斷
@Entity
@Table(name = "canned_message")
public class CannedMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "canned_id")
	private Integer cannedId;

	@Column(name = "canned_category_id", nullable = false)
	private Integer cannedCategoryId;

	@Column(name = "title", nullable = false, length = 100)
	private String title;

	@Column(name = "message_content", nullable = false, columnDefinition = "TEXT")
	private String messageContent;

	@Column(name = "category", length = 50)
	private String category;

	@Column(name = "keyword", length = 255)
	private String keyword;

	@Column(name = "is_system", nullable = false)
	private Integer isSystem;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	public Integer getCannedId() {
		return cannedId;
	}

	public void setCannedId(Integer cannedId) {
		this.cannedId = cannedId;
	}

	public Integer getCannedCategoryId() {
		return cannedCategoryId;
	}

	public void setCannedCategoryId(Integer cannedCategoryId) {
		this.cannedCategoryId = cannedCategoryId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMessageContent() {
		return messageContent;
	}

	public void setMessageContent(String messageContent) {
		this.messageContent = messageContent;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public Integer getIsSystem() {
		return isSystem;
	}

	public void setIsSystem(Integer isSystem) {
		this.isSystem = isSystem;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

}
