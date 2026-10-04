package com.fruitude.orders.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_sku")
public class ProductSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sku_id")
    private Integer skuId;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "sku_name", length = 50)
    private String skuName;

    @Column(name = "another_name", length = 50)
    private String anotherName;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "safety_stock")
    private Integer safetyStock;

    // 注意：資料表欄位名稱就是 intbound_qty（疑似 inbound 拼錯），這裡要跟資料庫一致
    @Column(name = "intbound_qty")
    private Integer intboundQty;

    @Column(name = "outbound_qty")
    private Integer outboundQty;

    @Column(name = "price")
    private Integer price;

    // tinyint，狀態代碼
    @Column(name = "status")
    private Integer status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "all_comment_amount")
    private Integer allCommentAmount;

    @Column(name = "all_comment_star")
    private Integer allCommentStar;

    public Integer getSkuId() {
        return skuId;
    }

    public void setSkuId(Integer skuId) {
        this.skuId = skuId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
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

    public Integer getIntboundQty() {
        return intboundQty;
    }

    public void setIntboundQty(Integer intboundQty) {
        this.intboundQty = intboundQty;
    }

    public Integer getOutboundQty() {
        return outboundQty;
    }

    public void setOutboundQty(Integer outboundQty) {
        this.outboundQty = outboundQty;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
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
}
