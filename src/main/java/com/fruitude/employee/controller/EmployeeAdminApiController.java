package com.fruitude.employee.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeeAdminService;
import com.fruitude.employee.model.EmployeePermissionFunction;
import com.fruitude.employee.model.EmployeePosition;
import com.fruitude.employee.model.OperationAuditLog;
import com.fruitude.employee.model.OperationAuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 後台員工管理 API：提供員工帳號、權限配置與權限功能資料的讀寫介面。
 */
@RestController
@RequestMapping("/api/admin/employees")
public class EmployeeAdminApiController {

	private final EmployeeAdminService employeeAdminService;
	private final OperationAuditService auditService;

	public EmployeeAdminApiController(EmployeeAdminService employeeAdminService,
			OperationAuditService auditService) {
		this.employeeAdminService = employeeAdminService;
		this.auditService = auditService;
	}

	/** 取得員工清單，並附上每位員工目前的權限數量。 */
	@GetMapping
	public List<Map<String, Object>> findEmployees(
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "status", required = false) Integer status) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (Employee employee : employeeAdminService.findEmployees(keyword, status)) {
			result.add(employeeResponse(employee));
		}
		return result;
	}

	/** 新增員工帳號。 */
	@Transactional
	@PostMapping
	public ResponseEntity<Map<String, Object>> createEmployee(@RequestBody EmployeeForm form,
			HttpServletRequest request) {
		// 操作紀錄的 employee_id 必須對應目前登入者，先擋下未登入請求，避免資料庫只回傳模糊的 NULL 錯誤。
		if (auditService.resolveEmployeeId(request) == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(messageObject("請先登入後台員工帳號，再新增員工"));
		}
		Employee employee = employeeAdminService.createEmployee(form.getEmployeeName(), form.getEmployeeAccount(),
				form.getEmployeePassword(), form.getEmployeePhone(), form.getEmployeeEmail(), form.getPositionId());
		auditService.record(request, "EMPLOYEE", "CREATE", employee.getEmployeeId(), employee.getEmployeeName(),
				"建立員工帳號 " + employee.getEmployeeAccount());
		return ResponseEntity.status(HttpStatus.CREATED).body(employeeResponse(employee));
	}

	private Map<String, Object> messageObject(String text) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 修改員工姓名、帳號、電話與 Email。 */
	@Transactional
	@PutMapping("/{employeeId}")
	public Map<String, Object> updateEmployee(@PathVariable("employeeId") Integer employeeId,
			@RequestBody EmployeeForm form, HttpServletRequest request) {
		Employee employee = employeeAdminService.updateEmployee(employeeId, form.getEmployeeName(),
				form.getEmployeeAccount(), form.getEmployeePhone(), form.getEmployeeEmail(), form.getPositionId());
		auditService.record(request, "EMPLOYEE", "UPDATE", employeeId, employee.getEmployeeName(),
				"更新員工基本資料與職位");
		return employeeResponse(employee);
	}

	/** 啟用或停用員工帳號。 */
	@Transactional
	@PutMapping("/{employeeId}/status")
	public Map<String, Object> updateStatus(@PathVariable("employeeId") Integer employeeId,
			@RequestBody StatusForm form, HttpServletRequest request) {
		Employee employee = employeeAdminService.updateStatus(employeeId, form.getEmployeeStatus());
		auditService.record(request, "EMPLOYEE", "STATUS", employeeId, employee.getEmployeeName(),
				"員工帳號狀態變更為" + (form.getEmployeeStatus() == 1 ? "正常" : "停用"));
		return employeeResponse(employee);
	}

	/** 重設員工密碼，API 回應不包含密碼或密碼雜湊。 */
	@Transactional
	@PutMapping("/{employeeId}/password")
	public Map<String, Object> resetPassword(@PathVariable("employeeId") Integer employeeId,
			@RequestBody PasswordForm form, HttpServletRequest request) {
		Employee employee = employeeAdminService.resetPassword(employeeId, form.getEmployeePassword());
		auditService.record(request, "EMPLOYEE", "PASSWORD", employeeId, employee.getEmployeeName(),
				"重設員工密碼（未保存密碼內容）");
		return employeeResponse(employee);
	}

	/** 取得所有可分配的權限功能。 */
	@GetMapping("/permission-functions")
	public List<EmployeePermissionFunction> findPermissionFunctions() {
		return employeeAdminService.findPermissionFunctions();
	}

	/** 取得所有員工職位，供職位管理與員工表單使用。 */
	@GetMapping("/positions")
	public List<EmployeePosition> findPositions() {
		return employeeAdminService.findPositions();
	}

	/** 新增員工職位。 */
	@Transactional
	@PostMapping("/positions")
	public ResponseEntity<EmployeePosition> createPosition(@RequestBody PositionForm form,
			HttpServletRequest request) {
		EmployeePosition position = employeeAdminService.createPosition(form.getPositionCode(),
				form.getPositionName(), form.getPositionDescription());
		auditService.record(request, "POSITION", "CREATE", position.getPositionId(), position.getPositionName(),
				"建立職位 " + position.getPositionCode());
		return ResponseEntity.status(HttpStatus.CREATED).body(position);
	}

	/** 修改職位名稱與說明，職位代碼維持不變。 */
	@Transactional
	@PutMapping("/positions/{positionId}")
	public EmployeePosition updatePosition(@PathVariable("positionId") Integer positionId,
			@RequestBody PositionForm form, HttpServletRequest request) {
		EmployeePosition position = employeeAdminService.updatePosition(positionId, form.getPositionName(),
				form.getPositionDescription());
		auditService.record(request, "POSITION", "UPDATE", positionId, position.getPositionName(),
				"更新職位名稱與說明");
		return position;
	}

	/** 啟用或停用員工職位。 */
	@Transactional
	@PutMapping("/positions/{positionId}/status")
	public EmployeePosition updatePositionStatus(@PathVariable("positionId") Integer positionId,
			@RequestBody PositionStatusForm form, HttpServletRequest request) {
		EmployeePosition position = employeeAdminService.updatePositionStatus(positionId, form.getPositionStatus());
		auditService.record(request, "POSITION", "STATUS", positionId, position.getPositionName(),
				"職位狀態變更為" + (form.getPositionStatus() == 1 ? "啟用" : "停用"));
		return position;
	}

	/** 修改權限功能的顯示資料，權限代碼不接受變更。 */
	@Transactional
	@PutMapping("/permission-functions/{permissionId}")
	public EmployeePermissionFunction updatePermissionFunction(
			@PathVariable("permissionId") Integer permissionId,
			@RequestBody PermissionFunctionForm form, HttpServletRequest request) {
		EmployeePermissionFunction function = employeeAdminService.updatePermissionFunction(permissionId,
				form.getPermissionName(), form.getPermissionDescription(), form.getPermissionGroup());
		auditService.record(request, "PERMISSION_FUNCTION", "UPDATE", permissionId,
				function.getPermissionName(), "更新權限功能顯示資料");
		return function;
	}

	/** 取得指定員工目前擁有的權限編號。 */
	@GetMapping("/{employeeId}/permissions")
	public Map<String, Object> findEmployeePermissions(@PathVariable("employeeId") Integer employeeId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("employeeId", employeeId);
		body.put("permissionIds", employeeAdminService.findEmployeePermissionIds(employeeId));
		return body;
	}

	/** 一次取代指定員工的權限清單。 */
	@Transactional
	@PutMapping("/{employeeId}/permissions")
	public Map<String, Object> replaceEmployeePermissions(@PathVariable("employeeId") Integer employeeId,
			@RequestBody PermissionAssignmentForm form, HttpServletRequest request) {
		Employee employee = employeeAdminService.findEmployee(employeeId);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("employeeId", employeeId);
		List<Integer> permissionIds = employeeAdminService.replaceEmployeePermissions(employeeId, form.getPermissionIds());
		body.put("permissionIds", permissionIds);
		auditService.record(request, "EMPLOYEE_PERMISSION", "ASSIGN", employeeId, employee.getEmployeeName(),
				"儲存權限配置，共 " + permissionIds.size() + " 項");
		return body;
	}

	/** 依關鍵字、模組、動作與日期查詢後台更改紀錄。 */
	@GetMapping("/audit-logs")
	public Map<String, Object> findAuditLogs(
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "module", required = false) String module,
			@RequestParam(name = "action", required = false) String action,
			@RequestParam(name = "startDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(name = "endDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Page<OperationAuditLog> logs = auditService.searchEmployeeAuditLogs(keyword, module, action, startDate, endDate, page, size);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("logs", logs.getContent());
		body.put("page", logs.getNumber());
		body.put("totalPages", logs.getTotalPages());
		body.put("totalElements", logs.getTotalElements());
		return body;
	}

	/** 輸入格式或業務規則錯誤時，以統一 JSON 格式回傳 400。 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException error) {
		return ResponseEntity.badRequest().body(message(error.getMessage()));
	}

	/** 找不到資料時回傳 404。 */
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException error) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(error.getMessage()));
	}

	/** 資料庫唯一鍵或外鍵衝突時回傳容易理解的精確訊息。 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, String>> handleConflict(DataIntegrityViolationException error) {
		String detail = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
		Throwable root = error.getRootCause();
		if (root != null && root.getMessage() != null) {
			detail += " " + root.getMessage().toLowerCase();
		}

		if (detail.contains("uk_employee_account")) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message("此員工帳號已被使用，請更換其他帳號"));
		}
		if (detail.contains("uk_employee_email")) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message("此電子信箱已被使用，請更換其他信箱"));
		}
		if (detail.contains("uk_employee_phone")) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message("此聯絡電話已被使用，請更換其他電話"));
		}
		if (detail.contains("uk_position_code")) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message("此職位代碼已被使用，請更換職位代碼"));
		}
		if (detail.contains("uk_position_name")) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message("此職位名稱已被使用，請更換職位名稱"));
		}
		if (detail.contains("foreign key") || detail.contains("fk_")) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message("此項目仍被其他資料關聯使用中，無法執行操作"));
		}
		if (detail.contains("cannot be null")) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message("必填欄位缺少或無效，請檢查輸入資料"));
		}
		return ResponseEntity.status(HttpStatus.CONFLICT).body(message("資料存取約束衝突，無法完成操作"));
	}

	// 將員工實體轉成頁面需要的資料，刻意不包含密碼。
	private Map<String, Object> employeeResponse(Employee employee) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("employeeId", employee.getEmployeeId());
		body.put("employeeName", employee.getEmployeeName());
		body.put("employeeAccount", employee.getEmployeeAccount());
		body.put("employeePhone", employee.getEmployeePhone());
		body.put("employeeEmail", employee.getEmployeeEmail());
		body.put("positionId", employee.getPositionId());
		EmployeePosition position = employeeAdminService.findPosition(employee.getPositionId());
		body.put("positionCode", position.getPositionCode());
		body.put("positionName", position.getPositionName());
		body.put("employeeStatus", employee.getEmployeeStatus());
		body.put("createdAt", employee.getCreatedAt());
		body.put("lastLoginAt", employee.getLastLoginAt());
		body.put("permissionCount", employeeAdminService.countPermissions(employee.getEmployeeId()));
		return body;
	}

	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 員工新增／修改表單；修改時密碼欄位會被忽略。 */
	public static class EmployeeForm {
		private String employeeName;
		private String employeeAccount;
		private String employeePassword;
		private String employeePhone;
		private String employeeEmail;
		private Integer positionId;
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
	}

	/** 職位新增／修改表單；修改時 positionCode 會由服務層忽略。 */
	public static class PositionForm {
		private String positionCode;
		private String positionName;
		private String positionDescription;
		public String getPositionCode() { return positionCode; }
		public void setPositionCode(String positionCode) { this.positionCode = positionCode; }
		public String getPositionName() { return positionName; }
		public void setPositionName(String positionName) { this.positionName = positionName; }
		public String getPositionDescription() { return positionDescription; }
		public void setPositionDescription(String positionDescription) { this.positionDescription = positionDescription; }
	}

	/** 職位狀態變更表單。 */
	public static class PositionStatusForm {
		private Integer positionStatus;
		public Integer getPositionStatus() { return positionStatus; }
		public void setPositionStatus(Integer positionStatus) { this.positionStatus = positionStatus; }
	}

	/** 員工狀態變更表單。 */
	public static class StatusForm {
		private Integer employeeStatus;
		public Integer getEmployeeStatus() { return employeeStatus; }
		public void setEmployeeStatus(Integer employeeStatus) { this.employeeStatus = employeeStatus; }
	}

	/** 員工密碼重設表單。 */
	public static class PasswordForm {
		private String employeePassword;
		public String getEmployeePassword() { return employeePassword; }
		public void setEmployeePassword(String employeePassword) { this.employeePassword = employeePassword; }
	}

	/** 權限分配表單，完整清單代表儲存後員工應擁有的權限。 */
	public static class PermissionAssignmentForm {
		private List<Integer> permissionIds;
		public List<Integer> getPermissionIds() { return permissionIds; }
		public void setPermissionIds(List<Integer> permissionIds) { this.permissionIds = permissionIds; }
	}

	/** 權限功能顯示資料修改表單，不包含不可變更的 permissionCode。 */
	public static class PermissionFunctionForm {
		private String permissionName;
		private String permissionDescription;
		private String permissionGroup;
		public String getPermissionName() { return permissionName; }
		public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
		public String getPermissionDescription() { return permissionDescription; }
		public void setPermissionDescription(String permissionDescription) { this.permissionDescription = permissionDescription; }
		public String getPermissionGroup() { return permissionGroup; }
		public void setPermissionGroup(String permissionGroup) { this.permissionGroup = permissionGroup; }
	}
}
