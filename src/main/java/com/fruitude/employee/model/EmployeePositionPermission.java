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
 * 職位預設權限關聯：保存每一個職位預先勾選的基本功能。
 */
@Entity
@Table(name = "employee_position_permission", uniqueConstraints = {
		@UniqueConstraint(name = "uk_position_permission_pair", columnNames = { "position_id", "permission_id" })
})
public class EmployeePositionPermission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "position_permission_id")
	private Integer positionPermissionId;

	// 對應 employee_position.position_id。
	@Column(name = "position_id", nullable = false)
	private Integer positionId;

	// 對應 employee_permission_function.permission_id。
	@Column(name = "permission_id", nullable = false)
	private Integer permissionId;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private LocalDateTime assignedAt;

	/** 第一次保存時自動記錄設定時間。 */
	@PrePersist
	public void applyAssignedAt() {
		if (assignedAt == null) assignedAt = LocalDateTime.now();
	}

	public Integer getPositionPermissionId() { return positionPermissionId; }
	public void setPositionPermissionId(Integer positionPermissionId) { this.positionPermissionId = positionPermissionId; }
	public Integer getPositionId() { return positionId; }
	public void setPositionId(Integer positionId) { this.positionId = positionId; }
	public Integer getPermissionId() { return permissionId; }
	public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }
	public LocalDateTime getAssignedAt() { return assignedAt; }
	public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}
