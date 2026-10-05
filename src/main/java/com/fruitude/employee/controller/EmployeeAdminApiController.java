package com.fruitude.employee.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.dao.DataIntegrityViolationException;
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

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeeAdminService;
import com.fruitude.employee.model.EmployeePermissionFunction;
import com.fruitude.employee.model.EmployeePosition;

/**
 * 後台員工管理 API：提供員工帳號、權限配置與權限功能資料的讀寫介面。
 */
@RestController
@RequestMapping("/api/admin/employees")
public class EmployeeAdminApiController {

	private final EmployeeAdminService employeeAdminService;

	public EmployeeAdminApiController(EmployeeAdminService employeeAdminService) {
		this.employeeAdminService = employeeAdminService;
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
	@PostMapping
	public ResponseEntity<Map<String, Object>> createEmployee(@RequestBody EmployeeForm form) {
		Employee employee = employeeAdminService.createEmployee(form.getEmployeeName(), form.getEmployeeAccount(),
				form.getEmployeePassword(), form.getEmployeePhone(), form.getEmployeeEmail(), form.getPositionId());
		return ResponseEntity.status(HttpStatus.CREATED).body(employeeResponse(employee));
	}

	/** 修改員工姓名、帳號、電話與 Email。 */
	@PutMapping("/{employeeId}")
	public Map<String, Object> updateEmployee(@PathVariable("employeeId") Integer employeeId,
			@RequestBody EmployeeForm form) {
		return employeeResponse(employeeAdminService.updateEmployee(employeeId, form.getEmployeeName(),
				form.getEmployeeAccount(), form.getEmployeePhone(), form.getEmployeeEmail(), form.getPositionId()));
	}

	/** 啟用或停用員工帳號。 */
	@PutMapping("/{employeeId}/status")
	public Map<String, Object> updateStatus(@PathVariable("employeeId") Integer employeeId,
			@RequestBody StatusForm form) {
		return employeeResponse(employeeAdminService.updateStatus(employeeId, form.getEmployeeStatus()));
	}

	/** 重設員工密碼，API 回應不包含密碼或密碼雜湊。 */
	@PutMapping("/{employeeId}/password")
	public Map<String, Object> resetPassword(@PathVariable("employeeId") Integer employeeId,
			@RequestBody PasswordForm form) {
		return employeeResponse(employeeAdminService.resetPassword(employeeId, form.getEmployeePassword()));
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
	@PostMapping("/positions")
	public ResponseEntity<EmployeePosition> createPosition(@RequestBody PositionForm form) {
		EmployeePosition position = employeeAdminService.createPosition(form.getPositionCode(),
				form.getPositionName(), form.getPositionDescription());
		return ResponseEntity.status(HttpStatus.CREATED).body(position);
	}

	/** 修改職位名稱與說明，職位代碼維持不變。 */
	@PutMapping("/positions/{positionId}")
	public EmployeePosition updatePosition(@PathVariable("positionId") Integer positionId,
			@RequestBody PositionForm form) {
		return employeeAdminService.updatePosition(positionId, form.getPositionName(),
				form.getPositionDescription());
	}

	/** 啟用或停用員工職位。 */
	@PutMapping("/positions/{positionId}/status")
	public EmployeePosition updatePositionStatus(@PathVariable("positionId") Integer positionId,
			@RequestBody PositionStatusForm form) {
		return employeeAdminService.updatePositionStatus(positionId, form.getPositionStatus());
	}

	/** 修改權限功能的顯示資料，權限代碼不接受變更。 */
	@PutMapping("/permission-functions/{permissionId}")
	public EmployeePermissionFunction updatePermissionFunction(
			@PathVariable("permissionId") Integer permissionId,
			@RequestBody PermissionFunctionForm form) {
		return employeeAdminService.updatePermissionFunction(permissionId, form.getPermissionName(),
				form.getPermissionDescription(), form.getPermissionGroup());
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
	@PutMapping("/{employeeId}/permissions")
	public Map<String, Object> replaceEmployeePermissions(@PathVariable("employeeId") Integer employeeId,
			@RequestBody PermissionAssignmentForm form) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("employeeId", employeeId);
		body.put("permissionIds", employeeAdminService.replaceEmployeePermissions(
				employeeId, form.getPermissionIds()));
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

	/** 資料庫唯一鍵或外鍵衝突時回傳容易理解的訊息。 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, String>> handleConflict(DataIntegrityViolationException error) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(message("資料重複或仍被其他資料使用，無法完成操作"));
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
