package com.fruitude.employee.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/** 員工密碼驗證信寄送服務；寄件帳號與應用程式密碼沿用既有 Gmail 環境設定。 */
@Service
public class EmployeePasswordResetMailService {

	private final JavaMailSender mailSender;
	private final String mailUsername;
	private final String fromAddress;
	private final String publicBaseUrl;

	public EmployeePasswordResetMailService(JavaMailSender mailSender,
			@Value("${spring.mail.username:}") String mailUsername,
			@Value("${app.mail.from:}") String fromAddress,
			@Value("${app.public-base-url}") String publicBaseUrl) {
		this.mailSender = mailSender;
		this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
		this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}

	/** 沒有設定寄件帳號時明確回報，不讓管理員誤以為信件已寄出。 */
	public boolean isConfigured() {
		return !mailUsername.isEmpty() && !fromAddress.isEmpty();
	}

	/** 寄出員工專用的一次性密碼設定連結，不在 Log 中輸出原始 Token。 */
	public void sendResetLink(String recipient, String employeeName, String rawToken, long minutesToLive) {
		String resetUrl = UriComponentsBuilder.fromUriString(publicBaseUrl + "/admin/employees/reset-password")
				.queryParam("resetToken", rawToken).build().encode().toUriString();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(recipient);
		message.setSubject("鮮果鋪員工帳號密碼設定");
		message.setText(employeeName + " 您好：\n\n"
				+ "管理人員已為您的鮮果鋪員工帳號申請密碼設定，請使用下方連結完成信箱確認並設定新密碼：\n"
				+ resetUrl + "\n\n"
				+ "此連結將於 " + minutesToLive + " 分鐘後失效，且只能使用一次。\n"
				+ "如果您不清楚這次操作，請不要點擊連結並聯絡系統管理人員。");
		mailSender.send(message);
	}
}
