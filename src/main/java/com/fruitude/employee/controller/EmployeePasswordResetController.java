package com.fruitude.employee.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeePasswordResetMailService;
import com.fruitude.employee.model.EmployeePasswordResetService;
import com.fruitude.employee.model.OperationAuditService;

import jakarta.servlet.http.HttpServletRequest;

/** 員工密碼設定 API：管理員寄信，員工持一次性 Token 驗證後自行設定密碼。 */
@RestController
@RequestMapping("/api/admin/employees")
public class EmployeePasswordResetController {

	private final EmployeePasswordResetService resetService;
	private final EmployeePasswordResetMailService mailService;
	private final OperationAuditService auditService;

	public EmployeePasswordResetController(EmployeePasswordResetService resetService,
			EmployeePasswordResetMailService mailService, OperationAuditService auditService) {
		this.resetService = resetService;
		this.mailService = mailService;
		this.auditService = auditService;
	}

	/** 管理員只負責寄送驗證信，不能直接替員工指定或看到新密碼。 */
	@PostMapping("/{employeeId}/password-reset-email")
	public ResponseEntity<?> sendResetEmail(@PathVariable("employeeId") Integer employeeId,
			HttpServletRequest request) {
		if (!mailService.isConfigured()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(message("寄信功能尚未設定，請先設定 Gmail 寄件帳號"));
		}
		try {
			Employee employee = resetService.requestReset(employeeId);
			auditService.record(request, "EMPLOYEE", "PASSWORD_EMAIL", employeeId, employee.getEmployeeName(),
					"寄送員工密碼設定驗證信至 " + maskEmail(employee.getEmployeeEmail()));
			return ResponseEntity.ok(message("驗證信已寄送至 " + maskEmail(employee.getEmployeeEmail())));
		} catch (IllegalArgumentException error) {
			return ResponseEntity.badRequest().body(message(error.getMessage()));
		} catch (MailException error) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(message("驗證信目前無法寄出，請稍後再試"));
		}
	}

	/** 員工開啟信件連結時，先檢查 Token 是否仍可使用。 */
	@GetMapping("/password/reset/validate")
	public ResponseEntity<?> validate(@RequestParam("token") String token) {
		if (!resetService.isValid(token)) {
			return ResponseEntity.badRequest()
					.body(message("密碼設定連結已過期或已使用，請聯絡管理人員重新寄送"));
		}
		return ResponseEntity.ok(message("信箱確認完成，請設定新密碼"));
	}

	/** 使用有效的一次性 Token 更新密碼，成功後該連結立刻失效。 */
	@PostMapping("/password/reset")
	public ResponseEntity<?> reset(@RequestBody ResetPasswordForm form) {
		try {
			resetService.resetPassword(form.getToken(), form.getNewPassword(), form.getConfirmPassword());
			return ResponseEntity.ok(message("密碼已更新，請使用新密碼登入後台"));
		} catch (IllegalArgumentException error) {
			return ResponseEntity.badRequest().body(message(error.getMessage()));
		}
	}

	private String maskEmail(String email) {
		if (email == null || !email.contains("@")) return "已登記信箱";
		String[] parts = email.split("@", 2);
		String visible = parts[0].isEmpty() ? "*" : parts[0].substring(0, 1) + "***";
		return visible + "@" + parts[1];
	}

	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 設定新密碼表單包含一次性 Token 與兩次密碼輸入。 */
	public static class ResetPasswordForm {
		private String token;
		private String newPassword;
		private String confirmPassword;
		public String getToken() { return token; }
		public void setToken(String token) { this.token = token; }
		public String getNewPassword() { return newPassword; }
		public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
		public String getConfirmPassword() { return confirmPassword; }
		public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
	}
}
