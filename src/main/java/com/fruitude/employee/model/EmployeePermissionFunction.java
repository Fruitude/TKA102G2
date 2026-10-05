package com.fruitude.employee.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 權限功能實體：定義後台可以分配給員工的功能代碼、名稱、說明與群組。
 */
@Entity
@Table(name = "employee_permission_function")
public class EmployeePermissionFunction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "permission_id")
	private Integer permissionId;

	// 權限代碼會由程式用來判斷權限，建立後不可透過管理頁修改。
	@Column(name = "permission_code", length = 50, nullable = false, unique = true, updatable = false)
	private String permissionCode;

	@Column(name = "permission_name", length = 100, nullable = false)
	private String permissionName;

	@Column(name = "permission_description", length = 255)
	private String permissionDescription;

	@Column(name = "permission_group", length = 50, nullable = false)
	private String permissionGroup;

	public Integer getPermissionId() { return permissionId; }
	public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }
	public String getPermissionCode() { return permissionCode; }
	public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
	public String getPermissionName() { return permissionName; }
	public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
	public String getPermissionDescription() { return permissionDescription; }
	public void setPermissionDescription(String permissionDescription) { this.permissionDescription = permissionDescription; }
	public String getPermissionGroup() { return permissionGroup; }
	public void setPermissionGroup(String permissionGroup) { this.permissionGroup = permissionGroup; }
}
