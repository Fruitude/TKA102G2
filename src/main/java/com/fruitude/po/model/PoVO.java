package com.fruitude.po.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.fruitude.employee.model.Employee;
import com.fruitude.podetail.model.PoDetailVO;
import com.fruitude.vendor.model.VendorVO;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


@Entity
@Table(name = "purchaseorder")
public class PoVO implements java.io.Serializable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    @Column(name = "id",updatable = false)
    private Integer poId;                       // 採購單系統編號

    @NotBlank(message = "採購單編號請勿空白")
    @Size(max = 15, message = "採購單編號長度不可超過15個字")
    @Column(name = "po_no", nullable = false, length = 15, unique = true)
    private String poNo;                      // 採購單編號
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    @NotNull(message = "供應商編號，請勿空白")
    private VendorVO vendor;         // 供應商編號

    @ManyToOne(fetch = FetchType.LAZY, optional = false) 
    @JoinColumn(name = "po_employee_id", nullable = false)                                      
    @NotNull(message = "採購員工編號請勿空白")                                                  
    private Employee poEmployeeId;             // 採購員工編號

    @NotNull(message = "採購日期請勿空白")
    @Column(name = "order_date", nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime orderDate;          // 採購日期

    @NotNull(message = "採購單狀態，請勿空白")
    @Min(value = 0, message = "採購單狀態值不正確")
    @Max(value = 3, message = "採購單狀態值不正確")
    @Column(name = "po_status", nullable = false)
    private Byte poStatus = 0;                // 採購單狀態 0=待審核,1=申請通過,2=申請未通過,3=已取消

    @NotNull(message = "採購單總金額，請勿空白")
    @Min(value = 0, message = "總金額不可為負數")
    @Max(value = 10000000, message = "總金額不可超過10,000,000")   // 新增這一行
    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;              // 總金額

    @ManyToOne(fetch = FetchType.LAZY)         // 可為 null
    @JoinColumn(name = "inbound_employee_id")
    private Employee inboundEmployeeId;        // 驗收員工編號

    @Column(name = "inbound_date")            // 可為 null
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime inboundDate;        // 驗收日期

    @NotNull(message = "驗收狀態，請勿空白")
    @Min(value = 0, message = "驗收狀態值不正確")
    @Max(value = 3, message = "驗收狀態值不正確")
    @Column(name = "inbound_status", nullable = false)
    private Byte inboundStatus = 0;           // 驗收狀態 0=尚未驗收,1=驗收成功,2=驗收失敗

    @NotNull(message = "驗收實付金額，請勿空白")
    @Min(value = 0, message = "驗收實付金額不可為負數")
    @Column(name = "inbound_amount", nullable = false)
    private Integer inboundAmount = 0;        // 驗收實付金額
    
    @Valid
    @OneToMany(mappedBy = "poId", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("poDetailId asc")
    private List<PoDetailVO> poDetails = new ArrayList<>();

	public PoVO() {}

	// 待審核（poStatus = 0）的採購單才能修改，頁面以 poVO.editable 判斷
	public boolean isEditable() {
		return poStatus != null && poStatus == 0;
	}

	// 申請通過（poStatus = 1）且尚未驗收（inboundStatus = 0）的採購單才能驗收，頁面以 poVO.receivable 判斷
	public boolean isReceivable() {
		return poStatus != null && poStatus == 1 && inboundStatus != null && inboundStatus == 0;
	}

	public Integer getPoId() {
		return poId;
	}

	public String getPoNo() {
		return poNo;
	}

	public VendorVO getVendor() {
		return vendor;
	}

	public Employee getPoEmployeeId() {
		return poEmployeeId;
	}

	public LocalDateTime getOrderDate() {
		return orderDate;
	}

	public Byte getPoStatus() {
		return poStatus;
	}

	public Integer getTotalAmount() {
		return totalAmount;
	}

	public Employee getInboundEmployeeId() {
		return inboundEmployeeId;
	}

	public LocalDateTime getInboundDate() {
		return inboundDate;
	}

	public Byte getInboundStatus() {
		return inboundStatus;
	}

	public Integer getInboundAmount() {
		return inboundAmount;
	}

	public List<PoDetailVO> getPoDetails() {
		return poDetails;
	}

	public void setPoId(Integer poId) {
		this.poId = poId;
	}

	public void setPoNo(String poNo) {
		this.poNo = poNo;
	}

	public void setVendor(VendorVO vendor) {
		this.vendor = vendor;
	}

	public void setPoEmployeeId(Employee poEmployeeId) {
		this.poEmployeeId = poEmployeeId;
	}

	public void setOrderDate(LocalDateTime orderDate) {
		this.orderDate = orderDate;
	}

	public void setPoStatus(Byte poStatus) {
		this.poStatus = poStatus;
	}

	public void setTotalAmount(Integer totalAmount) {
		this.totalAmount = totalAmount;
	}

	public void setInboundEmployeeId(Employee inboundEmployeeId) {
		this.inboundEmployeeId = inboundEmployeeId;
	}

	public void setInboundDate(LocalDateTime inboundDate) {
		this.inboundDate = inboundDate;
	}

	public void setInboundStatus(Byte inboundStatus) {
		this.inboundStatus = inboundStatus;
	}

	public void setInboundAmount(Integer inboundAmount) {
		this.inboundAmount = inboundAmount;
	}

	public void setPoDetails(List<PoDetailVO> poDetails) {
		this.poDetails = poDetails;
	}
    


    
}
