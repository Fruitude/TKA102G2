package com.fruitude.refundorder.model;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.fruitude.orders.model.Orders;
import com.fruitude.employee.model.Employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "refundorder")
public class RefundOrderVO implements java.io.Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refundorder_id", updatable = false)
    private Integer refundOrderId;            // 退款單編號

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orders_id", nullable = false, unique = true)
    @NotNull(message = "訂單編號，請勿空白")
    private Orders orders;                  // 訂單編號（一筆訂單只能有一張退款單）

    @NotNull(message = "退款申請日期，請勿空白")
    @Column(name = "refund_date", nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime refundDate;         // 退款申請日期

    @NotNull(message = "退款金額，請勿空白")
    @Min(value = 1, message = "退款金額必須大於0")
    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;             // 退款金額

    @NotNull(message = "退款原因，請勿空白")
    @Min(value = 0, message = "退款原因值不正確")
    @Max(value = 5, message = "退款原因值不正確")
    @Column(name = "refund_reason", nullable = false)
    private Byte refundReason = 0;            // 退款原因 0=其他,1=取消訂單,2=商品損毀或腐敗,3=商品品項或規格有誤,4=物流原因損毀,5=商品缺貨

    @Size(max = 255, message = "退款原因細節長度不可超過255個字")
    @Column(name = "reason_detail", length = 255)
    private String reasonDetail;              // 退款原因細節

    @NotNull(message = "退款狀態，請勿空白")
    @Min(value = 0, message = "退款狀態值不正確")
    @Max(value = 3, message = "退款狀態值不正確")
    @Column(name = "status", nullable = false)
    private Byte status = 0;                  // 退款狀態 0=待審核,1=處理中,2=已退款,3=審核未通過

    @NotNull(message = "退款方式，請勿空白")
    @Min(value = 0, message = "退款方式值不正確")
    @Max(value = 3, message = "退款方式值不正確")
    @Column(name = "payment_method", nullable = false)
    private Byte paymentMethod = 0;           // 退款方式 0=其他,1=銀行轉帳,2=信用卡退刷,3=退回第三方支付戶頭

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_handler", nullable = false)
    @NotNull(message = "承辦員工，請勿空白")
    private Employee caseHandler;             // 承辦員工

    public RefundOrderVO() {
    }

    public Integer getRefundOrderId() {
        return refundOrderId;
    }

    public void setRefundOrderId(Integer refundOrderId) {
        this.refundOrderId = refundOrderId;
    }

    public Orders getOrders() {
        return orders;
    }

    public void setOrders(Orders orders) {
        this.orders = orders;
    }

    public LocalDateTime getRefundDate() {
        return refundDate;
    }

    public void setRefundDate(LocalDateTime refundDate) {
        this.refundDate = refundDate;
    }

    public Integer getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Integer refundAmount) {
        this.refundAmount = refundAmount;
    }

    public Byte getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(Byte refundReason) {
        this.refundReason = refundReason;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public void setReasonDetail(String reasonDetail) {
        this.reasonDetail = reasonDetail;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public Byte getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(Byte paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Employee getCaseHandler() {
        return caseHandler;
    }

    public void setCaseHandler(Employee caseHandler) {
        this.caseHandler = caseHandler;
    }
}