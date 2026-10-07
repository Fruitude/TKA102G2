package com.fruitude.employee.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 後台操作紀錄服務：統一解析操作者、來源 IP、寫入紀錄及分頁查詢。 */
@Service
@Transactional(readOnly = true)
public class OperationAuditService {

	private static final List<String> ALL_EMPLOYEE_MODULES = List.of(
			"EMPLOYEE", "員工帳號", "員工",
			"POSITION", "職位管理", "職位",
			"EMPLOYEE_PERMISSION", "權限配置", "員工權限",
			"PERMISSION_FUNCTION", "權限功能");

	private final OperationAuditLogRepository auditLogRepository;
	private final EmployeeRepository employeeRepository;

	public OperationAuditService(OperationAuditLogRepository auditLogRepository,
			EmployeeRepository employeeRepository) {
		this.auditLogRepository = auditLogRepository;
		this.employeeRepository = employeeRepository;
	}

	/** 成功完成後台異動後寫入一筆紀錄，密碼等敏感內容不得放進 detail。 */
	@Transactional
	public OperationAuditLog record(HttpServletRequest request, String module, String action,
			Object targetId, String targetDisplay, String detail) {
		OperationAuditLog log = new OperationAuditLog();
		Integer employeeId = resolveEmployeeId(request);
		log.setEmployeeId(employeeId);
		log.setEmployeeName(resolveEmployeeName(employeeId));
		log.setTargetModule(requireText(module, 50));
		log.setActionType(requireText(action, 50));
		log.setTargetId(requireText(String.valueOf(targetId), 50));
		log.setTargetDisplay(requireText(targetDisplay, 100));
		log.setDetailContent(limit(detail, 4000));
		log.setIpAddress(resolveIpAddress(request));
		return auditLogRepository.save(log);
	}

	/**
	 * 查詢員工與權限管理操作紀錄：
	 * 嚴格限定只回傳員工帳號、職位、權限配置、權限功能模組，不包含會員、購物金或退款。
	 */
	public Page<OperationAuditLog> searchEmployeeAuditLogs(String keyword, String module, String action,
			LocalDate startDate, LocalDate endDate, int page, int size) {
		if (page < 0) throw new IllegalArgumentException("頁碼不可小於 0");
		if (size < 1 || size > 100) throw new IllegalArgumentException("每頁筆數必須介於 1 到 100");
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("開始日期不可晚於結束日期");
		}
		LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
		LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "auditId")));

		Collection<String> allowedModules = resolveModules(module);
		return auditLogRepository.searchEmployeeAuditLogs(clean(keyword), allowedModules, clean(action),
				startAt, endAt, pageable);
	}

	/** 依使用者選取的模組代碼對應合法資料庫值；未選時回傳全部員工模組。 */
	private Collection<String> resolveModules(String module) {
		String clean = clean(module).toUpperCase();
		if ("EMPLOYEE".equals(clean) || "員工帳號".equals(clean) || "員工".equals(clean)) {
			return List.of("EMPLOYEE", "員工帳號", "員工");
		}
		if ("POSITION".equals(clean) || "職位管理".equals(clean) || "職位".equals(clean)) {
			return List.of("POSITION", "職位管理", "職位");
		}
		if ("EMPLOYEE_PERMISSION".equals(clean) || "權限配置".equals(clean) || "員工權限".equals(clean)) {
			return List.of("EMPLOYEE_PERMISSION", "權限配置", "員工權限");
		}
		if ("PERMISSION_FUNCTION".equals(clean) || "權限功能".equals(clean)) {
			return List.of("PERMISSION_FUNCTION", "權限功能");
		}
		return ALL_EMPLOYEE_MODULES;
	}

	/** 購物金流水也需要操作者編號，因此提供相同的 Session 解析規則。 */
	public Integer resolveEmployeeId(HttpServletRequest request) {
		if (request == null) return null;
		HttpSession session = request.getSession(false);
		if (session == null) return null;
		Object value = session.getAttribute("loggedInEmployeeId");
		return value instanceof Number ? ((Number) value).intValue() : null;
	}

	private String resolveEmployeeName(Integer employeeId) {
		if (employeeId == null) return "系統管理員";
		return employeeRepository.findById(employeeId).map(Employee::getEmployeeName).orElse("系統管理員");
	}

	private String resolveIpAddress(HttpServletRequest request) {
		if (request == null) return null;
		String forwarded = request.getHeader("X-Forwarded-For");
		String address = forwarded == null || forwarded.trim().isEmpty()
				? request.getRemoteAddr() : forwarded.split(",")[0].trim();
		return limit(address, 50);
	}

	private String requireText(String value, int maxLength) {
		String clean = clean(value);
		if (clean.isEmpty()) throw new IllegalArgumentException("操作紀錄欄位不可空白");
		return limit(clean, maxLength);
	}

	private String clean(String value) { return value == null ? "" : value.trim(); }
	private String limit(String value, int maxLength) {
		if (value == null) return null;
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
