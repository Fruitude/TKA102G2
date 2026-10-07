package com.fruitude.employee.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeeAdminService;
import com.fruitude.employee.model.EmployeePosition;
import com.fruitude.employee.model.OperationAuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 後台員工登入 Session API：提供右上角帳號選單需要的登入資料與操作。 */
@RestController
@RequestMapping("/api/admin/session")
public class EmployeeSessionController {

	private final EmployeeAdminService employeeAdminService;
	private final OperationAuditService auditService;

	public EmployeeSessionController(EmployeeAdminService employeeAdminService,
			OperationAuditService auditService) {
		this.employeeAdminService = employeeAdminService;
		this.auditService = auditService;
	}

	/** 回傳目前登入員工；尚未登入時仍回傳 200，讓共用 topbar 顯示登入入口。 */
	@GetMapping
	public Map<String, Object> current(HttpServletRequest request) {
		Integer employeeId = sessionEmployeeId(request);
		if (employeeId == null) return unauthenticated();
		try {
			return employeeResponse(employeeAdminService.findEmployee(employeeId));
		} catch (java.util.NoSuchElementException error) {
			HttpSession session = request.getSession(false);
			if (session != null) session.invalidate();
			return unauthenticated();
		}
	}

	/** 驗證帳號密碼並建立後台員工 Session。 */
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest form, HttpServletRequest request) {
		try {
			Employee employee = employeeAdminService.login(form.getAccount(), form.getPassword());
			request.getSession(true).setAttribute("loggedInEmployeeId", employee.getEmployeeId());
			return ResponseEntity.ok(employeeResponse(employee));
		} catch (IllegalArgumentException error) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message(error.getMessage()));
		}
	}

	/** 公開員工申請：資料先保存為待審核，未經系統管理員核准不可登入。 */
	@PostMapping("/applications")
	public ResponseEntity<?> apply(@RequestBody ApplicationRequest form) {
		try {
			Employee employee = employeeAdminService.submitEmployeeApplication(form.getEmployeeName(),
					form.getEmployeeAccount(), form.getEmployeePassword(), form.getEmployeePhone(),
					form.getEmployeeEmail(), form.getPositionId());
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("message", "申請已送出，請等待系統管理員審核");
			body.put("employeeId", employee.getEmployeeId());
			return ResponseEntity.status(HttpStatus.CREATED).body(body);
		} catch (IllegalArgumentException error) {
			return ResponseEntity.badRequest().body(message(error.getMessage()));
		}
	}

	/** 清除後台員工 Session；未登入時重複登出也視為成功。 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) session.invalidate();
		return ResponseEntity.noContent().build();
	}

	/** 登入員工修改自己的密碼，成功後保留目前 Session。 */
	@PutMapping("/password")
	public ResponseEntity<?> changePassword(@RequestBody PasswordRequest form, HttpServletRequest request) {
		Integer employeeId = sessionEmployeeId(request);
		if (employeeId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("請先登入後台"));
		}
		try {
			if (form.getNewPassword() == null || !form.getNewPassword().equals(form.getConfirmPassword())) {
				throw new IllegalArgumentException("新密碼與確認密碼不一致");
			}
			Employee employee = employeeAdminService.changeOwnPassword(employeeId,
					form.getCurrentPassword(), form.getNewPassword());
			auditService.record(request, "EMPLOYEE", "PASSWORD", employeeId, employee.getEmployeeName(),
					"員工由右上角選單修改自己的密碼（未保存密碼內容）");
			return ResponseEntity.ok(message("密碼已更新"));
		} catch (IllegalArgumentException error) {
			return ResponseEntity.badRequest().body(message(error.getMessage()));
		}
	}

	/** 讓登入員工自行修改聯絡電話與電子信箱，其餘員工欄位保持不變。 */
	@PutMapping("/profile")
	public ResponseEntity<?> updateProfile(@RequestBody ProfileRequest form, HttpServletRequest request) {
		Integer employeeId = sessionEmployeeId(request);
		if (employeeId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("請先登入後台"));
		}
		try {
			Employee before = employeeAdminService.findEmployee(employeeId);
			String oldPhone = before.getEmployeePhone();
			String oldEmail = before.getEmployeeEmail();
			Employee employee = employeeAdminService.updateOwnContact(employeeId, form.getEmployeePhone(),
					form.getEmployeeEmail());
			if (!oldPhone.equals(employee.getEmployeePhone()) || !oldEmail.equals(employee.getEmployeeEmail())) {
				auditService.record(request, "EMPLOYEE", "UPDATE", employeeId, employee.getEmployeeName(),
						"員工由右上角選單更新聯絡電話與電子信箱");
			}
			return ResponseEntity.ok(employeeResponse(employee));
		} catch (IllegalArgumentException error) {
			return ResponseEntity.badRequest().body(message(error.getMessage()));
		}
	}

	// 右上角只回傳畫面需要的欄位，絕不回傳密碼雜湊。
	private Map<String, Object> employeeResponse(Employee employee) {
		Map<String, Object> body = new LinkedHashMap<>();
		EmployeePosition position = employeeAdminService.findPosition(employee.getPositionId());
		body.put("authenticated", true);
		body.put("employeeId", employee.getEmployeeId());
		body.put("employeeName", employee.getEmployeeName());
		body.put("employeeAccount", employee.getEmployeeAccount());
		body.put("employeePhone", employee.getEmployeePhone());
		body.put("employeeEmail", employee.getEmployeeEmail());
		body.put("positionName", position.getPositionName());
		body.put("employeeStatus", employee.getEmployeeStatus());
		body.put("lastLoginAt", employee.getLastLoginAt());
		return body;
	}

	private Map<String, Object> unauthenticated() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("authenticated", false);
		return body;
	}

	private Integer sessionEmployeeId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) return null;
		Object value = session.getAttribute("loggedInEmployeeId");
		return value instanceof Number ? ((Number) value).intValue() : null;
	}

	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 員工登入請求，只接收帳號與密碼。 */
	public static class LoginRequest {
		private String account;
		private String password;
		public String getAccount() { return account; }
		public void setAccount(String account) { this.account = account; }
		public String getPassword() { return password; }
		public void setPassword(String password) { this.password = password; }
	}

	/** 修改密碼請求；確認密碼只用於比對，不會寫入資料庫。 */
	public static class PasswordRequest {
		private String currentPassword;
		private String newPassword;
		private String confirmPassword;
		public String getCurrentPassword() { return currentPassword; }
		public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
		public String getNewPassword() { return newPassword; }
		public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
		public String getConfirmPassword() { return confirmPassword; }
		public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
	}

	/** 個人聯絡資料修改請求，只接受電話與電子信箱。 */
	public static class ProfileRequest {
		private String employeePhone;
		private String employeeEmail;
		public String getEmployeePhone() { return employeePhone; }
		public void setEmployeePhone(String employeePhone) { this.employeePhone = employeePhone; }
		public String getEmployeeEmail() { return employeeEmail; }
		public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }
	}

	/** 員工申請表單；密碼只會在後端雜湊後保存。 */
	public static class ApplicationRequest {
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
}
