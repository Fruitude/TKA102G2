package com.fruitude.member.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 會員服務層：集中處理註冊、登入、資料修改及後台管理規則。
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

	public static final int STATUS_DISABLED = 0;
	public static final int STATUS_ACTIVE = 1;
	private static final int PASSWORD_ITERATIONS = 120000;
	private static final int PASSWORD_KEY_LENGTH = 256;
	private static final int PASSWORD_SALT_LENGTH = 16;
	private static final Pattern REGISTRATION_ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9_]{6,20}$");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final MemberRepository memberRepository;
	private final MemberCreditTransactionRepository creditTransactionRepository;

	// 使用建構子注入，物件建立時就能確保會員與購物金流水 Repository 一定存在。
	public MemberService(MemberRepository memberRepository,
			MemberCreditTransactionRepository creditTransactionRepository) {
		this.memberRepository = memberRepository;
		this.creditTransactionRepository = creditTransactionRepository;
	}

	/** 註冊會員，並由系統設定編號、建立時間、啟用狀態及初始購物金。 */
	@Transactional
	public MemberVO register(MemberVO member) {
		// 去除不小心輸入的前後空白，Email 統一小寫後再進行格式與重複檢查。
		member.setMemberName(member.getMemberName().trim());
		member.setMemberAccount(member.getMemberAccount().trim());
		member.setMemberEmail(normalizeEmail(member.getMemberEmail()));
		validateRegistrationAccount(member.getMemberAccount());
		validateUniqueAccountAndEmail(member.getMemberAccount(), member.getMemberEmail(), null);

		member.setMemberId(null);
		member.setCreatedAt(null);
		member.setMemberStatus(STATUS_ACTIVE);
		member.setShoppingCredit(0);
		member.setMemberPassword(hashPassword(member.getMemberPassword()));
		return memberRepository.save(member);
	}

	/** 檢查新會員帳號是否符合 6 到 20 碼英數或底線的規則。 */
	public boolean isRegistrationAccountValid(String account) {
		return account != null && REGISTRATION_ACCOUNT_PATTERN.matcher(account.trim()).matches();
	}

	/** 檢查 Email 基本格式，完整格式仍會在正式註冊時由 Bean Validation 再驗證。 */
	public boolean isRegistrationEmailValid(String email) {
		return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
	}

	/** 即時檢查帳號是否尚未被其他會員使用。 */
	public boolean isAccountAvailable(String account) {
		return isRegistrationAccountValid(account)
				&& !memberRepository.existsByMemberAccountIgnoreCase(account.trim());
	}

	/** 即時檢查 Email 是否尚未被其他會員使用。 */
	public boolean isEmailAvailable(String email) {
		String normalizedEmail = normalizeEmail(email);
		return isRegistrationEmailValid(normalizedEmail)
				&& !memberRepository.existsByMemberEmailIgnoreCase(normalizedEmail);
	}

	/** 使用帳號或 Email 登入；停權會員即使密碼正確也不能登入。 */
	public Optional<MemberVO> login(String accountOrEmail, String password) {
		try {
			return Optional.of(loginOrThrow(accountOrEmail, password));
		} catch (IllegalArgumentException error) {
			return Optional.empty();
		}
	}

	/**
	 * 驗證會員登入並回傳明確的失敗原因，讓登入頁能分辨查無會員、密碼錯誤或帳號停權。
	 */
	public MemberVO loginOrThrow(String accountOrEmail, String password) {
		String loginName = accountOrEmail == null ? "" : accountOrEmail.trim();
		if (loginName.isEmpty()) {
			throw new IllegalArgumentException("請輸入會員帳號或電子郵件");
		}
		if (password == null || password.isEmpty()) {
			throw new IllegalArgumentException("請輸入密碼");
		}
		Optional<MemberVO> optional = memberRepository
				.findByMemberAccountIgnoreCaseOrMemberEmailIgnoreCase(loginName, loginName);
		if (!optional.isPresent()) {
			// 登入欄位同時接受帳號與 Email，因此依輸入格式顯示對應的錯誤原因。
			if (loginName.contains("@")) {
				throw new IllegalArgumentException("查無此電子郵件，請確認輸入內容或先完成註冊");
			}
			throw new IllegalArgumentException("帳號錯誤，查無此會員帳號，請確認輸入內容或先完成註冊");
		}

		MemberVO member = optional.get();
		if (!Integer.valueOf(STATUS_ACTIVE).equals(member.getMemberStatus())) {
			throw new IllegalArgumentException("此會員帳號目前已停權，請聯絡客服協助處理");
		}
		if (!passwordsMatch(password, member.getMemberPassword())) {
			throw new IllegalArgumentException("密碼錯誤，請重新輸入；若忘記密碼請聯絡客服");
		}
		return member;
	}

	/** 依會員編號取得單筆資料。 */
	public Optional<MemberVO> findById(Integer memberId) {
		return memberRepository.findById(memberId);
	}

	/** 判斷 Google 驗證信箱是否已經建立會員，避免註冊流程產生重複資料。 */
	public boolean hasMemberWithEmail(String email) {
		return memberRepository.findByMemberEmailIgnoreCase(normalizeEmail(email)).isPresent();
	}

	/** Google 驗證完成後，以已驗證的 Email 尋找可登入會員。 */
	public MemberVO loginWithVerifiedEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		MemberVO member = memberRepository.findByMemberEmailIgnoreCase(normalizedEmail)
				.orElseThrow(() -> new IllegalArgumentException("此 Google 信箱尚未註冊會員，請先完成註冊"));
		if (!Integer.valueOf(STATUS_ACTIVE).equals(member.getMemberStatus())) {
			throw new IllegalArgumentException("此會員帳號目前已停權，請聯絡客服協助處理");
		}
		return member;
	}

	/** 忘記密碼驗證成功後更新密碼；Controller 不可直接略過重設憑證呼叫此方法。 */
	@Transactional
	public void updatePasswordAfterVerification(Integer memberId, String newPassword) {
		if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
			throw new IllegalArgumentException("新密碼需為 8～72 個字");
		}
		MemberVO member = memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("找不到會員資料"));
		member.setMemberPassword(hashPassword(newPassword));
		memberRepository.save(member);
	}

	/**
	 * 後台會員清單使用資料庫分頁，並可同時套用關鍵字與狀態條件。
	 * 每頁上限設為 100，避免錯誤請求一次讀取過多資料。
	 */
	public Page<MemberVO> findAdminPage(String keyword, Integer memberStatus, int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("頁碼不可小於 0");
		}
		if (size < 1 || size > 100) {
			throw new IllegalArgumentException("每頁筆數必須介於 1 到 100");
		}
		if (memberStatus != null) {
			validateStatus(memberStatus);
		}
		String searchText = keyword == null ? "" : keyword.trim();
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "memberId"));
		return memberRepository.findAdminPage(searchText, memberStatus, pageable);
	}

	/**
	 * 修改個人資料。會員編號、建立時間、狀態與購物金不接受前台任意修改。
	 * 密碼留白時保留舊密碼，有填寫時才重新雜湊。
	 */
	@Transactional
	public Optional<MemberVO> updateProfile(Integer memberId, MemberVO form) {
		Optional<MemberVO> optional = memberRepository.findById(memberId);
		if (!optional.isPresent()) {
			return Optional.empty();
		}

		validateRequiredProfileFields(form);
		String memberName = form.getMemberName().trim();
		String memberAccount = form.getMemberAccount().trim();
		String memberEmail = normalizeEmail(form.getMemberEmail());
		validateUniqueAccountAndEmail(memberAccount, memberEmail, memberId);
		MemberVO member = optional.get();
		member.setMemberName(memberName);
		member.setMemberBirthday(form.getMemberBirthday());
		member.setMemberAccount(memberAccount);
		member.setMemberEmail(memberEmail);
		if (form.getMemberPassword() != null && !form.getMemberPassword().trim().isEmpty()) {
			member.setMemberPassword(hashPassword(form.getMemberPassword()));
		}
		return Optional.of(memberRepository.save(member));
	}

	/** 後台啟用或停權會員，只允許狀態 0 與 1。 */
	@Transactional
	public Optional<MemberVO> updateStatus(Integer memberId, Integer memberStatus) {
		validateStatus(memberStatus);
		Optional<MemberVO> optional = memberRepository.findById(memberId);
		if (!optional.isPresent()) {
			return Optional.empty();
		}

		MemberVO member = optional.get();
		member.setMemberStatus(memberStatus);
		return Optional.of(memberRepository.save(member));
	}

	/**
	 * 後台調整會員購物金，並在同一個交易內保存異動前後餘額。
	 * 只有餘額真的改變時才新增流水，避免產生沒有意義的零元紀錄。
	 */
	@Transactional
	public Optional<MemberVO> updateShoppingCredit(Integer memberId, Integer shoppingCredit,
			String reason, Integer employeeId) {
		if (shoppingCredit == null || shoppingCredit < 0) {
			throw new IllegalArgumentException("購物金不可小於 0");
		}
		String cleanReason = reason == null ? "" : reason.trim();
		if (cleanReason.isEmpty()) throw new IllegalArgumentException("請填寫購物金調整原因");
		if (cleanReason.length() > 255) throw new IllegalArgumentException("購物金調整原因最多 255 個字");
		Optional<MemberVO> optional = memberRepository.findById(memberId);
		if (!optional.isPresent()) {
			return Optional.empty();
		}

		MemberVO member = optional.get();
		int balanceBefore = member.getShoppingCredit() == null ? 0 : member.getShoppingCredit();
		if (balanceBefore == shoppingCredit.intValue()) return Optional.of(member);

		member.setShoppingCredit(shoppingCredit);
		MemberVO savedMember = memberRepository.save(member);

		MemberCreditTransaction transaction = new MemberCreditTransaction();
		transaction.setMemberId(memberId);
		transaction.setTransactionType(shoppingCredit > balanceBefore
				? MemberCreditTransaction.TYPE_ADMIN_ADD : MemberCreditTransaction.TYPE_ADMIN_DEDUCT);
		transaction.setAmount(shoppingCredit - balanceBefore);
		transaction.setBalanceBefore(balanceBefore);
		transaction.setBalanceAfter(shoppingCredit);
		transaction.setEmployeeId(employeeId);
		transaction.setReason(cleanReason);
		creditTransactionRepository.save(transaction);
		return Optional.of(savedMember);
	}

	/**
	 * 退款完成後將退款金額轉入會員購物金，並保存訂單及退款單來源。
	 * 此方法提供退款模組呼叫；同一張退款單只能成功入帳一次。
	 */
	@Transactional
	public MemberCreditTransaction addRefundCredit(Integer memberId, Integer amount,
			Integer ordersId, Integer refundOrderId, String reason) {
		if (memberId == null) throw new IllegalArgumentException("會員編號不可空白");
		if (amount == null || amount <= 0) throw new IllegalArgumentException("退款購物金必須大於 0");
		if (refundOrderId == null) throw new IllegalArgumentException("退款單編號不可空白");
		if (creditTransactionRepository.existsByRefundOrderId(refundOrderId)) {
			throw new IllegalArgumentException("這張退款單已轉入購物金");
		}
		String cleanReason = isBlank(reason) ? "退款轉入購物金" : reason.trim();
		if (cleanReason.length() > 255) throw new IllegalArgumentException("退款原因最多 255 個字");

		MemberVO member = memberRepository.findById(memberId)
				.orElseThrow(() -> new java.util.NoSuchElementException("找不到會員"));
		int balanceBefore = member.getShoppingCredit() == null ? 0 : member.getShoppingCredit();
		int balanceAfter;
		try {
			balanceAfter = Math.addExact(balanceBefore, amount);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("退款後購物金金額超出可保存範圍");
		}
		member.setShoppingCredit(balanceAfter);
		memberRepository.save(member);

		MemberCreditTransaction transaction = new MemberCreditTransaction();
		transaction.setMemberId(memberId);
		transaction.setTransactionType(MemberCreditTransaction.TYPE_REFUND);
		transaction.setAmount(amount);
		transaction.setBalanceBefore(balanceBefore);
		transaction.setBalanceAfter(balanceAfter);
		transaction.setOrdersId(ordersId);
		transaction.setRefundOrderId(refundOrderId);
		transaction.setReason(cleanReason);
		return creditTransactionRepository.save(transaction);
	}

	/** 集中分頁查詢所有會員的購物金異動紀錄，可搭配關鍵字、類型與日期區間。 */
	public Page<MemberCreditTransactionDTO> findAllCreditTransactions(String keyword, Byte type,
			java.time.LocalDate startDate, java.time.LocalDate endDate, int page, int size) {
		if (page < 0) throw new IllegalArgumentException("頁碼不可小於 0");
		if (size < 1 || size > 100) throw new IllegalArgumentException("每頁筆數必須介於 1 到 100");
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("開始日期不可晚於結束日期");
		}
		java.time.LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
		java.time.LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt")
						.and(Sort.by(Sort.Direction.DESC, "creditTransactionId")));
		String cleanKeyword = keyword == null ? "" : keyword.trim();
		return creditTransactionRepository.searchAllTransactions(cleanKeyword, type, startAt, endAt, pageable);
	}

	/** 依時間由新到舊讀取指定會員的購物金流水。 */
	public Page<MemberCreditTransaction> findCreditTransactions(Integer memberId, int page, int size) {
		if (!memberRepository.existsById(memberId)) throw new java.util.NoSuchElementException("找不到會員");
		if (page < 0) throw new IllegalArgumentException("頁碼不可小於 0");
		if (size < 1 || size > 100) throw new IllegalArgumentException("每頁筆數必須介於 1 到 100");
		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt")
						.and(Sort.by(Sort.Direction.DESC, "creditTransactionId")));
		return creditTransactionRepository.findByMemberId(memberId, pageable);
	}

	// 更新資料沒有另外建立表單類別，因此在服務層再次檢查不可缺少的欄位。
	private void validateRequiredProfileFields(MemberVO form) {
		if (form == null) {
			throw new IllegalArgumentException("會員資料不可空白");
		}
		if (isBlank(form.getMemberName())) {
			throw new IllegalArgumentException("會員姓名不可空白");
		}
		if (isBlank(form.getMemberAccount())) {
			throw new IllegalArgumentException("會員帳號不可空白");
		}
		if (isBlank(form.getMemberEmail())) {
			throw new IllegalArgumentException("電子郵件不可空白");
		}
		if (form.getMemberBirthday() == null) {
			throw new IllegalArgumentException("會員生日不可空白");
		}
		if (form.getMemberBirthday().isAfter(java.time.LocalDate.now())) {
			throw new IllegalArgumentException("生日不可晚於今天");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	// 新會員帳號規則只套用在註冊，避免影響資料庫中既有會員修改其他個人資料。
	private void validateRegistrationAccount(String account) {
		if (!isRegistrationAccountValid(account)) {
			throw new IllegalArgumentException("會員帳號需為 6～20 個英文字母、數字或底線");
		}
	}

	// Email 統一小寫保存，確保大小寫不同時仍會被視為同一個 Email。
	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private void validateUniqueAccountAndEmail(String account, String email, Integer currentMemberId) {
		boolean accountExists = currentMemberId == null
				? memberRepository.existsByMemberAccountIgnoreCase(account)
				: memberRepository.existsByMemberAccountIgnoreCaseAndMemberIdNot(account, currentMemberId);
		if (accountExists) {
			throw new IllegalArgumentException("此會員帳號已被使用");
		}

		boolean emailExists = currentMemberId == null
				? memberRepository.existsByMemberEmailIgnoreCase(email)
				: memberRepository.existsByMemberEmailIgnoreCaseAndMemberIdNot(email, currentMemberId);
		if (emailExists) {
			throw new IllegalArgumentException("此電子郵件已被使用");
		}
	}

	private void validateStatus(Integer memberStatus) {
		if (!Integer.valueOf(STATUS_DISABLED).equals(memberStatus)
				&& !Integer.valueOf(STATUS_ACTIVE).equals(memberStatus)) {
			throw new IllegalArgumentException("會員狀態只能是 0（停權）或 1（啟用）");
		}
	}

	/** 使用帶有隨機鹽值的 PBKDF2 雜湊密碼，資料庫不會保存使用者輸入的明文密碼。 */
	private String hashPassword(String password) {
		byte[] salt = new byte[PASSWORD_SALT_LENGTH];
		new SecureRandom().nextBytes(salt);
		byte[] hash = createPasswordHash(password.toCharArray(), salt, PASSWORD_ITERATIONS);
		return "pbkdf2$" + PASSWORD_ITERATIONS + "$"
				+ Base64.getEncoder().encodeToString(salt) + "$"
				+ Base64.getEncoder().encodeToString(hash);
	}

	private byte[] createPasswordHash(char[] password, byte[] salt, int iterations) {
		PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, PASSWORD_KEY_LENGTH);
		try {
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("目前環境不支援 PBKDF2 密碼雜湊", e);
		} finally {
			specification.clearPassword();
		}
	}

	private boolean passwordsMatch(String rawPassword, String storedPassword) {
		if (rawPassword == null || storedPassword == null) {
			return false;
		}

		try {
			String[] parts = storedPassword.split("\\$");
			if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
				return false;
			}
			int iterations = Integer.parseInt(parts[1]);
			byte[] salt = Base64.getDecoder().decode(parts[2]);
			byte[] storedHash = Base64.getDecoder().decode(parts[3]);
			byte[] enteredHash = createPasswordHash(rawPassword.toCharArray(), salt, iterations);
			return MessageDigest.isEqual(enteredHash, storedHash);
		} catch (IllegalArgumentException e) {
			// 資料庫中的密碼格式不正確時視為登入失敗，不把格式錯誤暴露給前端。
			return false;
		}
	}
}
