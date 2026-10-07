package com.fruitude.member.model;

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

/** 產生、驗證及使用一次性密碼重設憑證。 */
@Service
public class MemberPasswordResetService {

	private static final int TOKEN_BYTES = 32;

	private final MemberRepository memberRepository;
	private final MemberPasswordResetTokenRepository tokenRepository;
	private final MemberService memberService;
	private final PasswordResetMailService mailService;
	private final SecureRandom secureRandom = new SecureRandom();
	private final long minutesToLive;

	public MemberPasswordResetService(MemberRepository memberRepository,
			MemberPasswordResetTokenRepository tokenRepository, MemberService memberService,
			PasswordResetMailService mailService,
			@Value("${app.password-reset.minutes-to-live:15}") long minutesToLive) {
		this.memberRepository = memberRepository;
		this.tokenRepository = tokenRepository;
		this.memberService = memberService;
		this.mailService = mailService;
		this.minutesToLive = minutesToLive;
	}

	/**
	 * 信箱存在且會員啟用時寄出重設連結；查無信箱時不回傳差異，避免外部探查會員名單。
	 */
	@Transactional
	public void requestReset(String email) {
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
		Optional<MemberVO> optional = memberRepository.findByMemberEmailIgnoreCase(normalizedEmail);
		if (!optional.isPresent() || !Integer.valueOf(MemberService.STATUS_ACTIVE)
				.equals(optional.get().getMemberStatus())) {
			return;
		}

		MemberVO member = optional.get();
		LocalDateTime now = LocalDateTime.now();
		if (tokenRepository.existsByMemberMemberIdAndCreatedAtAfter(member.getMemberId(), now.minusMinutes(1))) {
			return;
		}
		tokenRepository.invalidateUnusedTokens(member.getMemberId(), now);

		byte[] randomBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(randomBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		MemberPasswordResetToken resetToken = new MemberPasswordResetToken();
		resetToken.setMember(member);
		resetToken.setTokenHash(hashToken(rawToken));
		resetToken.setExpiresAt(now.plusMinutes(minutesToLive));
		tokenRepository.save(resetToken);

		mailService.sendResetLink(member.getMemberEmail(), member.getMemberName(), rawToken, minutesToLive);
	}

	/** 前端開啟重設畫面時先檢查 Token 是否存在、未使用且仍在期限內。 */
	@Transactional(readOnly = true)
	public boolean isValid(String rawToken) {
		if (rawToken == null || rawToken.trim().isEmpty()) return false;
		Optional<MemberPasswordResetToken> optional = tokenRepository.findByTokenHash(hashToken(rawToken.trim()));
		return optional.isPresent() && isUsable(optional.get(), LocalDateTime.now());
	}

	/** 驗證 Token 後更新會員密碼，並立刻把 Token 標記為已使用。 */
	@Transactional
	public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
		if (newPassword == null || !newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException("新密碼與確認密碼不一致");
		}
		if (rawToken == null || rawToken.trim().isEmpty()) {
			throw new IllegalArgumentException("密碼重設連結不正確");
		}
		MemberPasswordResetToken token = tokenRepository.findByTokenHash(hashToken(rawToken.trim()))
				.orElseThrow(() -> new IllegalArgumentException("密碼重設連結不正確或已失效"));
		LocalDateTime now = LocalDateTime.now();
		if (!isUsable(token, now)) {
			throw new IllegalArgumentException("密碼重設連結已過期或已使用，請重新申請");
		}

		memberService.updatePasswordAfterVerification(token.getMember().getMemberId(), newPassword);
		token.setUsedAt(now);
		tokenRepository.save(token);
	}

	private boolean isUsable(MemberPasswordResetToken token, LocalDateTime now) {
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
