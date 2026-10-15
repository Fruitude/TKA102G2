package com.fruitude.employee.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集中整理登入員工可使用的後台權限，讓頁面、API 與側邊欄使用同一份判斷結果。
 */
@Service
public class EmployeeAdminAccessService {

	private static final byte STATUS_ACTIVE = 1;
	private static final byte REVIEW_APPROVED = 1;

	private final EmployeeRepository employeeRepository;
	private final EmployeePositionRepository positionRepository;
	private final EmployeePermissionRepository permissionRepository;
	private final EmployeePositionPermissionRepository positionPermissionRepository;
	private final EmployeePermissionGroupResolver permissionGroupResolver;

	public EmployeeAdminAccessService(EmployeeRepository employeeRepository,
			EmployeePositionRepository positionRepository,
			EmployeePermissionRepository permissionRepository,
			EmployeePositionPermissionRepository positionPermissionRepository,
			EmployeePermissionGroupResolver permissionGroupResolver) {
		this.employeeRepository = employeeRepository;
		this.positionRepository = positionRepository;
		this.permissionRepository = permissionRepository;
		this.positionPermissionRepository = positionPermissionRepository;
		this.permissionGroupResolver = permissionGroupResolver;
	}

	/**
	 * 只接受正常、已核准且職位仍啟用的員工；ADMIN 保留全部權限，避免唯一管理員被設定錯誤後鎖在後台外。
	 */
	@Transactional(readOnly = true)
	public Optional<AccessProfile> findAccessProfile(Integer employeeId) {
		if (employeeId == null) return Optional.empty();
		Optional<Employee> employeeResult = employeeRepository.findById(employeeId);
		if (employeeResult.isEmpty()) return Optional.empty();
		Employee employee = employeeResult.get();
		if (!isActive(employee.getEmployeeStatus()) || !isApproved(employee.getEmployeeReviewStatus())) {
			return Optional.empty();
		}

		Optional<EmployeePosition> positionResult = positionRepository.findById(employee.getPositionId());
		if (positionResult.isEmpty() || !isActive(positionResult.get().getPositionStatus())) {
			return Optional.empty();
		}
		EmployeePosition position = positionResult.get();
		boolean administrator = "ADMIN".equalsIgnoreCase(position.getPositionCode());
		Set<String> storedPermissionCodes = new LinkedHashSet<>();
		// 所有職位（包含 ADMIN）都使用相同規則，確保管理畫面的取消勾選與實際門禁一致。
		storedPermissionCodes.addAll(positionPermissionRepository.findPermissionCodesByPositionId(position.getPositionId()));
		storedPermissionCodes.addAll(permissionRepository.findPermissionCodesByEmployeeId(employeeId));
		Set<String> permissionCodes = permissionGroupResolver.expandStoredCodes(storedPermissionCodes);
		return Optional.of(new AccessProfile(employee, position, administrator, permissionCodes));
	}

	private boolean isActive(Byte status) {
		return status != null && status == STATUS_ACTIVE;
	}

	private boolean isApproved(Byte status) {
		return status != null && status == REVIEW_APPROVED;
	}

	/** 登入員工的後台身分與不可變權限集合。 */
	public static class AccessProfile {
		private final Employee employee;
		private final EmployeePosition position;
		private final boolean administrator;
		private final Set<String> permissionCodes;

		public AccessProfile(Employee employee, EmployeePosition position, boolean administrator,
				Set<String> permissionCodes) {
			this.employee = employee;
			this.position = position;
			this.administrator = administrator;
			this.permissionCodes = Collections.unmodifiableSet(new LinkedHashSet<>(permissionCodes));
		}

		public Employee getEmployee() { return employee; }
		public EmployeePosition getPosition() { return position; }
		public boolean isAdministrator() { return administrator; }
		public Set<String> getPermissionCodes() { return permissionCodes; }
	}
}
