package com.fruitude.chat.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// FAQ（faq 表）：客服聊天室的自動回覆內容，CannedReplyService.faqReply 用 keyword 比對會員訊息
// keyword 逗號分隔不同說法，& 表示同時出現；sort_order 是同一類別內的顯示順序，由管理頁拖拉排序決定
// 是否回覆由所屬類別（faq_category.status）決定，faq 本身沒有狀態
@Entity
@Table(name = "faq")
public class Faq {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "faq_id")
	private Integer faqId;

	@Column(name = "faq_category_id", nullable = false)
	private Integer faqCategoryId;

	@Column(name = "question", nullable = false, length = 255)
	private String question;

	@Column(name = "answer", nullable = false, columnDefinition = "TEXT")
	private String answer;

	@Column(name = "keyword", length = 255)
	private String keyword;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public Integer getFaqId() {
		return faqId;
	}

	public void setFaqId(Integer faqId) {
		this.faqId = faqId;
	}

	public Integer getFaqCategoryId() {
		return faqCategoryId;
	}

	public void setFaqCategoryId(Integer faqCategoryId) {
		this.faqCategoryId = faqCategoryId;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public String getAnswer() {
		return answer;
	}

	public void setAnswer(String answer) {
		this.answer = answer;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

}
