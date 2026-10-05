package com.fruitude.employee.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 員工權限關聯實體：記錄哪一位員工擁有哪些權限功能。
 */
@Entity
@Table(name = "employee_permission", uniqueConstraints = {
		@UniqueConstraint(name = "uk_employee_permission_pair", columnNames = { "employee_id", "permission_id" })
})
public class EmployeePermission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "emp_permission_id")
	private Integer employeePermissionId;

	// 使用編號保存關聯，服務層會先驗證員工與權限功能都存在。
	@Column(name = "employee_id", nullable = false)
	private Integer employeeId;

	@Column(name = "permission_id", nullable = false)
	private Integer permissionId;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private LocalDateTime assignedAt;

	/** 建立權限關聯時自動記錄分配時間。 */
	@PrePersist
	public void applyAssignedAt() {
		if (assignedAt == null) assignedAt = LocalDateTime.now();
	}

	public Integer getEmployeePermissionId() { return employeePermissionId; }
	public void setEmployeePermissionId(Integer employeePermissionId) { this.employeePermissionId = employeePermissionId; }
	public Integer getEmployeeId() { return employeeId; }
	public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }
	public Integer getPermissionId() { return permissionId; }
	public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }
	public LocalDateTime getAssignedAt() { return assignedAt; }
	public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}
