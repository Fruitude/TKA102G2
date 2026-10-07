package com.fruitude.member.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/** 會員驗證信寄送服務；寄件帳號及密碼只從環境變數讀取。 */
@Service
public class PasswordResetMailService {

	private final JavaMailSender mailSender;
	private final String mailUsername;
	private final String fromAddress;
	private final String publicBaseUrl;

	public PasswordResetMailService(JavaMailSender mailSender,
			@Value("${spring.mail.username:}") String mailUsername,
			@Value("${app.mail.from:}") String fromAddress,
			@Value("${app.public-base-url}") String publicBaseUrl) {
		this.mailSender = mailSender;
		this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
		this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}

	/** 沒有設定 Gmail 寄件帳號時，不接受忘記密碼請求，避免畫面假裝已寄出。 */
	public boolean isConfigured() {
		return !mailUsername.isEmpty() && !fromAddress.isEmpty();
	}

	/** 寄出只含一次性連結的純文字信件，不在 Log 或資料庫保存原始 Token。 */
	public void sendResetLink(String recipient, String memberName, String rawToken, long minutesToLive) {
		String resetUrl = UriComponentsBuilder.fromUriString(publicBaseUrl + "/front/about/login/")
				.queryParam("resetToken", rawToken).build().encode().toUriString();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(recipient);
		message.setSubject("鮮果鋪會員密碼重設");
		message.setText(memberName + " 您好：\n\n"
				+ "請使用下方連結重新設定鮮果鋪會員密碼：\n" + resetUrl + "\n\n"
				+ "此連結將於 " + minutesToLive + " 分鐘後失效，且只能使用一次。\n"
				+ "如果不是您提出申請，請忽略這封信，原密碼不會被變更。");
		mailSender.send(message);
	}
}
