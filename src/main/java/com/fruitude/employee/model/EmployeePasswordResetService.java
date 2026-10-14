package com.fruitude.employee.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 產生、驗證及使用員工的一次性密碼重設憑證。 */
@Service
public class EmployeePasswordResetService {

	private static final int TOKEN_BYTES = 32;

	private final EmployeeAdminService employeeAdminService;
	private final EmployeePasswordResetTokenRepository tokenRepository;
	private final EmployeePasswordResetMailService mailService;
	private final SecureRandom secureRandom = new SecureRandom();
	private final long minutesToLive;

	public EmployeePasswordResetService(EmployeeAdminService employeeAdminService,
			EmployeePasswordResetTokenRepository tokenRepository,
			EmployeePasswordResetMailService mailService,
			@Value("${app.password-reset.minutes-to-live:15}") long minutesToLive) {
		this.employeeAdminService = employeeAdminService;
		this.tokenRepository = tokenRepository;
		this.mailService = mailService;
		this.minutesToLive = minutesToLive;
	}

	/** 建立員工專用的一次性憑證並寄到員工資料中已登記的信箱。 */
	@Transactional
	public Employee requestReset(Integer employeeId) {
		Employee employee = employeeAdminService.findEmployee(employeeId);
		if (employee.getEmployeeStatus() == null || employee.getEmployeeStatus() != EmployeeAdminService.STATUS_ACTIVE
				|| employee.getEmployeeReviewStatus() == null
				|| employee.getEmployeeReviewStatus() != EmployeeAdminService.REVIEW_APPROVED) {
			throw new IllegalArgumentException("只有正常且已核准的員工帳號可以寄送密碼設定信");
		}

		LocalDateTime now = LocalDateTime.now();
		if (tokenRepository.existsByEmployeeEmployeeIdAndCreatedAtAfter(employeeId, now.minusMinutes(1))) {
			throw new IllegalArgumentException("此員工一分鐘內已寄送過驗證信，請稍後再試");
		}
		tokenRepository.invalidateUnusedTokens(employeeId, now);

		byte[] randomBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(randomBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		EmployeePasswordResetToken resetToken = new EmployeePasswordResetToken();
		resetToken.setEmployee(employee);
		resetToken.setTokenHash(hashToken(rawToken));
		resetToken.setExpiresAt(now.plusMinutes(minutesToLive));
		tokenRepository.save(resetToken);

		mailService.sendResetLink(employee.getEmployeeEmail(), employee.getEmployeeName(), rawToken, minutesToLive);
		return employee;
	}

	/** 員工開啟信件連結時，確認 Token 存在、尚未使用且仍在期限內。 */
	@Transactional(readOnly = true)
	public boolean isValid(String rawToken) {
		if (rawToken == null || rawToken.trim().isEmpty()) return false;
		Optional<EmployeePasswordResetToken> token = tokenRepository.findByTokenHash(hashToken(rawToken.trim()));
		return token.isPresent() && isUsable(token.get(), LocalDateTime.now());
	}

	/** 驗證一次性 Token 後更新員工密碼，完成時立刻標記 Token 已使用。 */
	@Transactional
	public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
		if (newPassword == null || !newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException("新密碼與確認密碼不一致");
		}
		if (rawToken == null || rawToken.trim().isEmpty()) {
			throw new IllegalArgumentException("密碼設定連結不正確");
		}
		EmployeePasswordResetToken token = tokenRepository.findByTokenHash(hashToken(rawToken.trim()))
				.orElseThrow(() -> new IllegalArgumentException("密碼設定連結不正確或已失效"));
		LocalDateTime now = LocalDateTime.now();
		if (!isUsable(token, now)) {
			throw new IllegalArgumentException("密碼設定連結已過期或已使用，請聯絡管理人員重新寄送");
		}

		employeeAdminService.updatePasswordAfterEmailVerification(token.getEmployee().getEmployeeId(), newPassword);
		token.setUsedAt(now);
		tokenRepository.save(token);
	}

	private boolean isUsable(EmployeePasswordResetToken token, LocalDateTime now) {
		return token.getUsedAt() == null && token.getExpiresAt() != null && token.getExpiresAt().isAfter(now);
	}

	private String hashToken(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
			return hex.toString();
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("目前環境不支援 SHA-256", error);
		}
	}
}
