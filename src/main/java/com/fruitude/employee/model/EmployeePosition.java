package com.fruitude.employee.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 員工職位實體：保存職位代碼、名稱、說明與啟用狀態。
 */
@Entity
@Table(name = "employee_position")
public class EmployeePosition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "position_id")
	private Integer positionId;

	// 職位代碼供程式辨識，建立後不允許從管理頁修改。
	@Column(name = "position_code", length = 30, nullable = false, unique = true, updatable = false)
	private String positionCode;

	@Column(name = "position_name", length = 50, nullable = false, unique = true)
	private String positionName;

	@Column(name = "position_description", length = 255)
	private String positionDescription;

	// 資料庫以 TINYINT 的 1／0 表示啟用或停用。
	@Column(name = "position_status", nullable = false, columnDefinition = "TINYINT")
	private Byte positionStatus;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	/** 新增職位時補上預設值與建立時間。 */
	@PrePersist
	public void applyDefaults() {
		LocalDateTime now = LocalDateTime.now();
		if (positionDescription == null) positionDescription = "";
		if (positionStatus == null) positionStatus = (byte) 1;
		if (createdAt == null) createdAt = now;
		if (updatedAt == null) updatedAt = now;
	}

	/** 修改職位時同步更新最後修改時間。 */
	@PreUpdate
	public void updateTimestamp() {
		updatedAt = LocalDateTime.now();
	}

	public Integer getPositionId() { return positionId; }
	public void setPositionId(Integer positionId) { this.positionId = positionId; }
	public String getPositionCode() { return positionCode; }
	public void setPositionCode(String positionCode) { this.positionCode = positionCode; }
	public String getPositionName() { return positionName; }
	public void setPositionName(String positionName) { this.positionName = positionName; }
	public String getPositionDescription() { return positionDescription; }
	public void setPositionDescription(String positionDescription) { this.positionDescription = positionDescription; }
	public Byte getPositionStatus() { return positionStatus; }
	public void setPositionStatus(Byte positionStatus) { this.positionStatus = positionStatus; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
