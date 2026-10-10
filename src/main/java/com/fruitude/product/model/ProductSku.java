package com.fruitude.product.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.LastModifiedDate;

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
@Table(name = "product_sku")
public class ProductSku implements java.io.Serializable {
	public static final Byte STATUS_DISCONTINUED = 4; // 永久停產

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "sku_id", updatable = false)
	private Integer skuId;
		
	@ManyToOne
	@JoinColumn(
			name = "product_id", 
			referencedColumnName = "product_id"
			)
	private Product product;
	
	@Column(name = "sku_name")
	private String skuName;

	@Column(name = "another_name")
	private String anotherName;
	
	@Column(name = "stock")
	private Integer stock = 0;
	
	@Column(name = "safety_stock")
	private Integer safetyStock = 0;
	
	@Column(name = "inbound_qty")
	private Integer inboundQty = 0;

	@Column(name = "outbound_qty")
	private Integer outboundQty = 0;

	// 缺貨（status 2）時最多可接的預購量；NULL 或 0 代表不接預購。預設 10
	@Column(name = "max_backorder_qty", columnDefinition = "INT DEFAULT 10")
	private Integer maxBackorderQty = 10;
	
	//採購預設購買數量，0表示預設不多買，20表示購買大於缺口數量20個
	@Column(name="purchase_add_on_qty",columnDefinition = "INT DEFAULT 20")
	private Integer purchaseAddOnQty=20;

	@Column(name = "price")
	private Integer price;
	
	@Column(name = "status")
	private Byte status = 0;
	
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
	
	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
	
	@Column(name = "all_comment_amount")
	private Integer allCommentAmount = 0;
	
	@Column(name = "all_comment_star")
	private Integer allCommentStar = 0;

	@OneToMany(mappedBy = "productSku",
			 cascade = CascadeType.ALL,
			 orphanRemoval = true)
	@OrderBy("sortOrder ASC, imageId ASC")
	private List<ProductImage> productImages = new ArrayList<>();
	
	public Integer getSkuId() {
		return skuId;
	}
	
	public void setSkuId(Integer skuId) {
		this.skuId = skuId;
	}
	
	public Product getProduct() {
	return product;
	}

	public void setProduct(Product product) {
	this.product = product;
	}
	
    @jakarta.persistence.Transient
    public String getDisplayName() {
        return resolveDisplayName(skuName, anotherName);
    }

    public static String resolveDisplayName(String name, String alias) {
        return alias != null && !alias.isBlank() ? alias.strip() : name;
    }

	public String getSkuName() {
		return skuName;
	}
	
	public void setSkuName(String skuName) {
		this.skuName = skuName;
	}
	
	public String getAnotherName() {
		return anotherName;
	}
	
	public void setAnotherName(String anotherName) {
		this.anotherName = anotherName;
	}
	
	public Integer getStock() {
		return stock;
	}
	
	public void setStock(Integer stock) {
		this.stock = stock;
	}
	
	public Integer getSafetyStock() {
		return safetyStock;
	}
	
	public void setSafetyStock(Integer safetyStock) {
		this.safetyStock = safetyStock;
	}
	
	public Integer getInboundQty() {
		return inboundQty;
	}
	
	public void setInboundQty(Integer inboundQty) {
		this.inboundQty = inboundQty;
	}
	
	public Integer getOutboundQty() {
		return outboundQty;
	}
	
	public void setOutboundQty(Integer outboundQty) {
		this.outboundQty = outboundQty;
	}

	public Integer getMaxBackorderQty() {
		return maxBackorderQty;
	}

	public void setMaxBackorderQty(Integer maxBackorderQty) {
		this.maxBackorderQty = maxBackorderQty;
	}
	
    public Integer getExpectedStock() {

        int stock = this.stock == null ? 0 : this.stock;
        int inboundQty = this.inboundQty == null ? 0 : this.inboundQty;
        int outboundQty = this.outboundQty == null ? 0 : this.outboundQty;

        return stock + inboundQty - outboundQty;
    }
    
    public List<String> getStockStatuses() {
        long current = stock == null ? 0L : stock.longValue();
        long safety = safetyStock == null ? 0L : safetyStock.longValue();
        long incoming = inboundQty == null ? 0L : inboundQty.longValue();
        long outgoing = outboundQty == null ? 0L : outboundQty.longValue();
        var alerts = new ArrayList<String>();
        if (current + incoming - outgoing < safety) alerts.add("低於安全庫存");
        if (safety == 0 && current < 10) alerts.add("庫存低於10");
        if (current > 100) alerts.add("庫存高於100");
        if (incoming > 100) alerts.add("待進貨超過100");
        else if (incoming > 50) alerts.add("待進貨超過50");
        if (outgoing > 100) alerts.add("待出貨超過100");
        else if (outgoing > 50) alerts.add("待出貨超過50");
        if (safety == 0) alerts.add("未設安全庫存");
        return alerts.isEmpty() ? List.of("正常") : List.copyOf(alerts);
    }

    public boolean isStockAbnormal() {
        return !getStockStatuses().equals(List.of("正常"));
    }

    public boolean isBelowSafetyStock() {

        int safetyStock = this.safetyStock == null ? 0 : this.safetyStock;

        return getExpectedStock() < safetyStock;
    }
	
	public Integer getPrice() {
		return price;
	}
	
	public void setPrice(Integer price) {
		this.price = price;
	}
	
    public String getStatusLabel() {
        if (status == null) return "未設定";
        return switch (status) { case 0 -> "下架"; case 1 -> "上架"; case 2 -> "缺貨"; case 3 -> "即將下架"; case 4 -> "永久停產"; case 5 -> "預備上架"; default -> "未設定"; };
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

	public List<ProductImage> getProductImages() {
	    return productImages;
	}

	public void setProductImages(List<ProductImage> productImages) {
	    this.productImages = productImages;
	}
	
	// 缺口數量：待出貨 + 安全庫存量 - 庫存量 - 待進貨
	public Integer getShortageQty() {

	    int safetyStock = this.safetyStock == null ? 0 : this.safetyStock;

	    return safetyStock - getExpectedStock();
	}
	
	public Integer getPurchaseAddOnQty() {
		return purchaseAddOnQty;
	}

	public void setPurchaseAddOnQty(Integer purchaseAddOnQty) {
		this.purchaseAddOnQty = purchaseAddOnQty;
	}
}
