package com.fruitude.chat.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// FAQ 類別（faq_category 表）：客服聊天室自動回覆用的 FAQ 分類
// parent_category_id 是上層類別，沒有上層時是 null；status 0=不顯示（停用）、1=顯示
// CannedReplyService.faqReply 只用「自己和所有上層都是 status=1」的類別底下的 FAQ 回覆
// 只對應欄位、不建關聯，上層類別用編號存
@Entity
@Table(name = "faq_category")
public class FaqCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "faq_category_id")
	private Integer faqCategoryId;

	@Column(name = "parent_category_id")
	private Integer parentCategoryId;

	@Column(name = "category_name", nullable = false, length = 50)
	private String categoryName;

	@Column(name = "category_desc", length = 255)
	private String categoryDesc;

	@Column(name = "status", nullable = false)
	private Integer status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public Integer getFaqCategoryId() {
		return faqCategoryId;
	}

	public void setFaqCategoryId(Integer faqCategoryId) {
		this.faqCategoryId = faqCategoryId;
	}

	public Integer getParentCategoryId() {
		return parentCategoryId;
	}

	public void setParentCategoryId(Integer parentCategoryId) {
		this.parentCategoryId = parentCategoryId;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}

	public String getCategoryDesc() {
		return categoryDesc;
	}

	public void setCategoryDesc(String categoryDesc) {
		this.categoryDesc = categoryDesc;
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

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

}
