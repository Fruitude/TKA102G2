package com.fruitude.member.controller;

import java.util.LinkedHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.MemberPasswordResetService;
import com.fruitude.member.model.PasswordResetMailService;

/** 會員忘記密碼 API：寄送一次性連結、驗證連結及更新密碼。 */
@RestController
@RequestMapping("/api/members/password")
public class MemberPasswordResetController {

	private final MemberPasswordResetService resetService;
	private final PasswordResetMailService mailService;

	public MemberPasswordResetController(MemberPasswordResetService resetService,
			PasswordResetMailService mailService) {
		this.resetService = resetService;
		this.mailService = mailService;
	}

	/** 無論信箱是否存在都回傳相同成功文字，避免洩漏會員名單。 */
	@PostMapping("/forgot")
	public ResponseEntity<?> forgot(@RequestBody ForgotPasswordForm form) {
		if (!mailService.isConfigured()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(message("寄信功能尚未設定，請先設定 Gmail 寄件帳號"));
		}
		try {
			resetService.requestReset(form.getEmail());
			return ResponseEntity.ok(message("如果此信箱已註冊，我們會寄出密碼重設連結"));
		} catch (MailException error) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(message("驗證信目前無法寄出，請稍後再試"));
		}
	}

	/** 開啟設定新密碼畫面前，先確認網址中的 Token 仍可使用。 */
	@GetMapping("/reset/validate")
	public ResponseEntity<?> validate(@RequestParam("token") String token) {
		if (!resetService.isValid(token)) {
			return ResponseEntity.badRequest().body(message("密碼重設連結已過期或已使用，請重新申請"));
		}
		return ResponseEntity.ok(message("密碼重設連結有效"));
	}

	/** 使用有效的一次性 Token 設定新密碼。 */
	@PostMapping("/reset")
	public ResponseEntity<?> reset(@RequestBody ResetPasswordForm form) {
		try {
			resetService.resetPassword(form.getToken(), form.getNewPassword(), form.getConfirmPassword());
			return ResponseEntity.ok(message("密碼已更新，請使用新密碼登入"));
		} catch (IllegalArgumentException error) {
			return ResponseEntity.badRequest().body(message(error.getMessage()));
		}
	}

	private LinkedHashMap<String, String> message(String text) {
		LinkedHashMap<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 忘記密碼表單只接受會員註冊 Email。 */
	public static class ForgotPasswordForm {
		private String email;
		public String getEmail() { return email; }
		public void setEmail(String email) { this.email = email; }
	}

	/** 設定新密碼表單包含一次性 Token 及兩次密碼輸入。 */
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
