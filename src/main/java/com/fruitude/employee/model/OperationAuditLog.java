package com.fruitude.employee.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 後台操作紀錄實體：保存誰在何時修改哪一個後台資料，供管理頁查詢與追蹤。
 */
@Entity
@Table(name = "operation_audit_log", indexes = {
		@Index(name = "idx_audit_created_at", columnList = "created_at"),
		@Index(name = "idx_audit_target", columnList = "target_module,target_id,created_at")
})
public class OperationAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "audit_id")
	private Integer auditId;

	// 尚未接上後台登入時允許為空；接上 loggedInEmployeeId 後會自動保存員工編號。
	@Column(name = "employee_id")
	private Integer employeeId;

	@Column(name = "employee_name", length = 50, nullable = false)
	private String employeeName;

	@Column(name = "target_module", length = 50, nullable = false)
	private String targetModule;

	@Column(name = "action_type", length = 50, nullable = false)
	private String actionType;

	@Column(name = "target_id", length = 50, nullable = false)
	private String targetId;

	@Column(name = "target_display", length = 100, nullable = false)
	private String targetDisplay;

	@Column(name = "detail_content", columnDefinition = "TEXT")
	private String detailContent;

	@Column(name = "ip_address", length = 50)
	private String ipAddress;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** 新增紀錄時由伺服器統一補上時間，避免前端偽造。 */
	@PrePersist
	public void applyCreatedAt() {
		if (createdAt == null) createdAt = LocalDateTime.now();
	}

	public Integer getAuditId() { return auditId; }
	public void setAuditId(Integer auditId) { this.auditId = auditId; }
	public Integer getEmployeeId() { return employeeId; }
	public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }
	public String getEmployeeName() { return employeeName; }
	public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
	public String getTargetModule() { return targetModule; }
	public void setTargetModule(String targetModule) { this.targetModule = targetModule; }
	public String getActionType() { return actionType; }
	public void setActionType(String actionType) { this.actionType = actionType; }
	public String getTargetId() { return targetId; }
	public void setTargetId(String targetId) { this.targetId = targetId; }
	public String getTargetDisplay() { return targetDisplay; }
	public void setTargetDisplay(String targetDisplay) { this.targetDisplay = targetDisplay; }
	public String getDetailContent() { return detailContent; }
	public void setDetailContent(String detailContent) { this.detailContent = detailContent; }
	public String getIpAddress() { return ipAddress; }
	public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
