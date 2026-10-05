package com.fruitude.product.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_category")
public class ProductCategory implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "product_category_id", updatable = false)
    private Integer productCategoryId;
	
	
	@Column(name = "category_name")
    private String categoryName;
	
	
	@ManyToOne
	@JoinColumn(
		name = "parent_category_id",
		referencedColumnName = "product_category_id"
	)
	private ProductCategory parentCategory;
    
	@Column(name = "category_desc")
	private String categoryDesc;

	@Column(name = "sort_order")
	private Integer sortOrder = 0;

	@Column(name = "status")
    private Byte status = 0 ;
    
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
	
	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
    
	public Integer getProductCategoryId() {
		return productCategoryId;
	}
	public void setProductCategoryId(Integer productCategoryId) {
		this.productCategoryId = productCategoryId;
	}
	public String getCategoryName() {
		return categoryName;
	}
	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}
	public ProductCategory getParentCategory() {
		return parentCategory;
	}
	public void setParentCategory(ProductCategory parentCategory) {
		this.parentCategory = parentCategory;
	}
	public String getCategoryDesc() {
		return categoryDesc;
	}
	public void setCategoryDesc(String categoryDesc) {
		this.categoryDesc = categoryDesc;
	}
	public Integer getSortOrder() {
		return sortOrder;
	}
	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}
	public Byte getStatus() {
		return status;
	}
	public void setStatus(Byte status) {
		this.status = status;
	}
	
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
    
   }
