package com.fruitude.product.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.LastModifiedDate;

import com.fruitude.vendor.model.VendorVO;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "product_id", updatable = false)
	private Integer productId;
	@ManyToOne
	@JoinColumn(
		name = "product_category_id",
		referencedColumnName = "product_category_id"
	)
	private ProductCategory productCategory;
	
	@ManyToOne
	@JoinColumn(
		name = "vendor_id",
		referencedColumnName = "vendor_id"
	)
	private VendorVO vendor;
	
	@Column(name = "product_name")
	private String productName;

	@Column(name = "product_desc")
	private String productDesc;

	@Column(name = "status")
	private Byte status = 0;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
	
	@Column(name = "all_comment_amount")
	private Integer allCommentAmount = 0;

	@Column(name = "all_comment_star")
	private Integer allCommentStar = 0;
	
	@OneToMany(mappedBy = "product",  
			cascade = CascadeType.ALL,
		    orphanRemoval = true)
	@OrderBy("skuId ASC")
	private List<ProductSku> productSkus = new ArrayList<>();
	
	public Integer getProductId() {
		return productId;
	}
	public void setProductId(Integer productId) {
		this.productId = productId;
	}
	public ProductCategory getProductCategory() {
		return productCategory;
	}
	public void setProductCategory(ProductCategory productCategory) {
		this.productCategory = productCategory;
	}
	public VendorVO getVendor() {
		return vendor;
	}
	public void setVendor(VendorVO vendor) {
		this.vendor = vendor;
	}
	public String getProductName() {
		return productName;
	}
	public void setProductName(String productName) {
		this.productName = productName;
	}
	public String getProductDesc() {
		return productDesc;
	}
	public void setProductDesc(String productDesc) {
		this.productDesc = productDesc;
	}
    public boolean isHasSellableSku() {
        return productSkus.stream().anyMatch(s -> ProductLifecycleService.isSellable(s.getStatus())
            && !(s.getStatus() == 3 && ProductLifecycleService.isDepleted(s)));
    }

    public boolean isHasReadySku() {
        return productSkus.stream().anyMatch(s -> Byte.valueOf((byte)5).equals(s.getStatus()));
    }

    public boolean isHasListedSku() {
        return productSkus.stream().anyMatch(s -> Byte.valueOf((byte)1).equals(s.getStatus()));
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
	
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	public Integer getAllCommentAmount() {
		return allCommentAmount;
	}
	public void setAllCommentAmount(Integer allCommentAmount) {
		this.allCommentAmount = allCommentAmount;
	}
	public Integer getAllCommentStar() {
		return allCommentStar;
	}
	public void setAllCommentStar(Integer allCommentStar) {
		this.allCommentStar = allCommentStar;
	}
	
	public List<ProductSku> getProductSkus() {
	    return productSkus;
	}

	public void setProductSkus(List<ProductSku> productSkus) {
	    this.productSkus = productSkus;
	}

	public ProductImage getFirstProductImage() {

	    if (productSkus == null || productSkus.isEmpty()) {
	        return null;
	    }

	    for (ProductSku sku : productSkus) {

	        if (sku.getProductImages() == null
	                || sku.getProductImages().isEmpty()) {
	            continue;
	        }

	        for (ProductImage image : sku.getProductImages()) {

	            if (image != null && image.getImageData() != null) {
	                return image;
	            }
	        }
	    }

	    return null;
	}
	
}
