package com.fruitude.orders.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders_detail")
public class OrdersDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orders_detail_id")
    private Integer ordersDetailId;

    @Column(name = "orders_id")
    private Integer ordersId;

    @Column(name = "sku_id")
    private Integer skuId;

    @Column(name = "product_name", length = 50)
    private String productName;

    @Column(name = "orders_quantity")
    private Integer ordersQuantity;

    @Column(name = "unit_price")
    private Integer unitPrice;

    @Column(name = "comment_text", length = 1000)
    private String commentText;

    @Column(name = "comment_date")
    private LocalDateTime commentDate;

    @Column(name = "comment_status")
    private Integer commentStatus;

    @Column(name = "comment_star")
    private Integer commentStar;

    @Column(name = "invoice_carrier", length = 20)
    private String invoiceCarrier;

    public Integer getOrdersDetailId() {
        return ordersDetailId;
    }

    public void setOrdersDetailId(Integer ordersDetailId) {
        this.ordersDetailId = ordersDetailId;
    }

    public Integer getOrdersId() {
        return ordersId;
    }

    public void setOrdersId(Integer ordersId) {
        this.ordersId = ordersId;
    }

    public Integer getSkuId() {
        return skuId;
    }

    public void setSkuId(Integer skuId) {
        this.skuId = skuId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getOrdersQuantity() {
        return ordersQuantity;
    }

    public void setOrdersQuantity(Integer ordersQuantity) {
        this.ordersQuantity = ordersQuantity;
    }

    public Integer getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Integer unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public LocalDateTime getCommentDate() {
        return commentDate;
    }

    public void setCommentDate(LocalDateTime commentDate) {
        this.commentDate = commentDate;
    }

    public Integer getCommentStatus() {
        return commentStatus;
    }

    public void setCommentStatus(Integer commentStatus) {
        this.commentStatus = commentStatus;
    }

    public Integer getCommentStar() {
        return commentStar;
    }

    public void setCommentStar(Integer commentStar) {
        this.commentStar = commentStar;
    }

    public String getInvoiceCarrier() {
        return invoiceCarrier;
    }

    public void setInvoiceCarrier(String invoiceCarrier) {
        this.invoiceCarrier = invoiceCarrier;
    }
}
