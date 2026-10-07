package com.fruitude.employee.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 員工帳號實體：每個物件對應 employee 資料表中的一筆後台員工資料。
 */
@Entity
@Table(name = "employee")
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "employee_id")
	private Integer employeeId;

	@Column(name = "employee_name", length = 50, nullable = false)
	private String employeeName;

	@Column(name = "employee_account", length = 50, nullable = false, unique = true)
	private String employeeAccount;

	// 密碼可以由表單寫入，但任何 JSON 回應都不應把雜湊值送到瀏覽器。
	@JsonIgnore
	@Column(name = "employee_password", length = 255, nullable = false)
	private String employeePassword;

	@Column(name = "employee_phone", length = 20, nullable = false)
	private String employeePhone;

	@Column(name = "employee_email", length = 100, nullable = false, unique = true)
	private String employeeEmail;

	// 保存職位外鍵；職位名稱由 employee_position 主檔統一管理。
	@Column(name = "position_id", nullable = false)
	private Integer positionId;

	// 資料庫以 TINYINT 的 1／0 表示正常或停用。
	@Column(name = "employee_status", nullable = false, columnDefinition = "TINYINT")
	private Byte employeeStatus;

	// 審核狀態與登入狀態分開：待審核帳號先保存資料，但不可登入。
	@Column(name = "employee_review_status", nullable = false, columnDefinition = "TINYINT")
	private Byte employeeReviewStatus;

	@Column(name = "reviewed_by_employee_id")
	private Integer reviewedByEmployeeId;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "rejection_reason", length = 255)
	private String rejectionReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	/** 新增員工時補上建立時間與預設啟用狀態。 */
	@PrePersist
	public void applyDefaults() {
		if (createdAt == null) createdAt = LocalDateTime.now();
		if (employeeStatus == null) employeeStatus = (byte) 1;
		if (employeeReviewStatus == null) employeeReviewStatus = (byte) 1;
	}

	public Integer getEmployeeId() { return employeeId; }
	public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }
	public String getEmployeeName() { return employeeName; }
	public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
	public String getEmployeeAccount() { return employeeAccount; }
	public void setEmployeeAccount(String employeeAccount) { this.employeeAccount = employeeAccount; }
	public String getEmployeePassword() { return employeePassword; }
	public void setEmployeePassword(String employeePassword) { this.employeePassword = employeePassword; }
	public String getEmployeePhone() { return employeePhone; }
	public void setEmployeePhone(String employeePhone) { this.employeePhone = employeePhone; }
	public String getEmployeeEmail() { return employeeEmail; }
	public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }
	public Integer getPositionId() { return positionId; }
	public void setPositionId(Integer positionId) { this.positionId = positionId; }
	public Byte getEmployeeStatus() { return employeeStatus; }
	public void setEmployeeStatus(Byte employeeStatus) { this.employeeStatus = employeeStatus; }
	public Byte getEmployeeReviewStatus() { return employeeReviewStatus; }
	public void setEmployeeReviewStatus(Byte employeeReviewStatus) { this.employeeReviewStatus = employeeReviewStatus; }
	public Integer getReviewedByEmployeeId() { return reviewedByEmployeeId; }
	public void setReviewedByEmployeeId(Integer reviewedByEmployeeId) { this.reviewedByEmployeeId = reviewedByEmployeeId; }
	public LocalDateTime getReviewedAt() { return reviewedAt; }
	public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
	public String getRejectionReason() { return rejectionReason; }
	public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getLastLoginAt() { return lastLoginAt; }
	public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
