package com.fruitude.employee.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 後台員工管理服務：集中處理員工欄位驗證、密碼雜湊與權限分配交易。
 */
@Service
@Transactional(readOnly = true)
public class EmployeeAdminService {

	public static final byte STATUS_DISABLED = 0;
	public static final byte STATUS_ACTIVE = 1;
	private static final int PASSWORD_ITERATIONS = 120000;
	private static final int PASSWORD_KEY_LENGTH = 256;
	private static final int PASSWORD_SALT_LENGTH = 16;
	private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9_]{4,50}$");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\- ]{6,20}$");

	private final EmployeeRepository employeeRepository;
	private final EmployeePermissionRepository employeePermissionRepository;
	private final EmployeePermissionFunctionRepository permissionFunctionRepository;
	private final EmployeePositionRepository positionRepository;

	/** 建構子注入三個 Repository，確保服務建立時依賴完整。 */
	public EmployeeAdminService(EmployeeRepository employeeRepository,
			EmployeePermissionRepository employeePermissionRepository,
			EmployeePermissionFunctionRepository permissionFunctionRepository,
			EmployeePositionRepository positionRepository) {
		this.employeeRepository = employeeRepository;
		this.employeePermissionRepository = employeePermissionRepository;
		this.permissionFunctionRepository = permissionFunctionRepository;
		this.positionRepository = positionRepository;
	}

	/** 依關鍵字與狀態篩選員工，並固定依員工編號排序。 */
	public List<Employee> findEmployees(String keyword, Integer status) {
		if (status != null) validateStatus(status);
		String searchText = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
		return employeeRepository.findAll().stream()
				.filter(employee -> status == null || employee.getEmployeeStatus().intValue() == status)
				.filter(employee -> searchText.isEmpty()
						|| contains(employee.getEmployeeName(), searchText)
						|| contains(employee.getEmployeeAccount(), searchText)
						|| contains(employee.getEmployeeEmail(), searchText))
				.sorted(Comparator.comparing(Employee::getEmployeeId))
				.collect(Collectors.toList());
	}

	/** 取得單一員工，不存在時回傳明確錯誤。 */
	public Employee findEmployee(Integer employeeId) {
		return employeeRepository.findById(employeeId)
				.orElseThrow(() -> new NoSuchElementException("找不到這位員工"));
	}

	/** 使用員工帳號與密碼登入，停用中的帳號不可進入後台。 */
	@Transactional
	public Employee login(String account, String password) {
		String cleanAccount = requireText(account, "請輸入員工帳號", 50, "員工帳號最多 50 個字");
		Employee employee = employeeRepository.findByEmployeeAccountIgnoreCase(cleanAccount)
				.orElseThrow(() -> new IllegalArgumentException("帳號或密碼錯誤"));
		if (employee.getEmployeeStatus() == null || employee.getEmployeeStatus() != STATUS_ACTIVE
				|| !passwordsMatch(password, employee.getEmployeePassword())) {
			throw new IllegalArgumentException("帳號或密碼錯誤，或此帳號已停用");
		}
		employee.setLastLoginAt(java.time.LocalDateTime.now());
		return employeeRepository.save(employee);
	}

	/** 登入員工修改自己的密碼前，必須先驗證目前密碼。 */
	@Transactional
	public Employee changeOwnPassword(Integer employeeId, String currentPassword, String newPassword) {
		Employee employee = findEmployee(employeeId);
		if (!passwordsMatch(currentPassword, employee.getEmployeePassword())) {
			throw new IllegalArgumentException("目前密碼不正確");
		}
		validatePassword(newPassword);
		if (passwordsMatch(newPassword, employee.getEmployeePassword())) {
			throw new IllegalArgumentException("新密碼不可與目前密碼相同");
		}
		employee.setEmployeePassword(hashPassword(newPassword));
		return employeeRepository.save(employee);
	}

	/** 允許登入員工修改自己的聯絡電話與電子信箱，姓名、帳號及職位不在此功能變更。 */
	@Transactional
	public Employee updateOwnContact(Integer employeeId, String phone, String email) {
		Employee employee = findEmployee(employeeId);
		String cleanPhone = requireText(phone, "聯絡電話不可空白", 20, "聯絡電話最多 20 個字");
		String cleanEmail = requireText(email, "電子信箱不可空白", 100, "電子信箱最多 100 個字")
				.toLowerCase(Locale.ROOT);
		if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
			throw new IllegalArgumentException("聯絡電話格式不正確");
		}
		if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
			throw new IllegalArgumentException("電子信箱格式不正確");
		}
		if (employeeRepository.existsByEmployeePhoneAndEmployeeIdNot(cleanPhone, employeeId)) {
			throw new IllegalArgumentException("聯絡電話【" + cleanPhone + "】已被其他員工使用");
		}
		if (employeeRepository.existsByEmployeeEmailIgnoreCaseAndEmployeeIdNot(cleanEmail, employeeId)) {
			throw new IllegalArgumentException("電子信箱【" + cleanEmail + "】已被其他員工使用");
		}
		employee.setEmployeePhone(cleanPhone);
		employee.setEmployeeEmail(cleanEmail);
		return employeeRepository.save(employee);
	}

	/** 新增員工，系統會統一欄位格式並將密碼雜湊後保存。 */
	@Transactional
	public Employee createEmployee(String name, String account, String password, String phone, String email,
			Integer positionId) {
		Employee employee = new Employee();
		applyProfile(employee, name, account, phone, email, positionId, null);
		validatePassword(password);
		employee.setEmployeePassword(hashPassword(password));
		employee.setEmployeeStatus(STATUS_ACTIVE);
		return employeeRepository.save(employee);
	}

	/** 修改員工基本資料；密碼與狀態由各自獨立的操作處理。 */
	@Transactional
	public Employee updateEmployee(Integer employeeId, String name, String account, String phone, String email,
			Integer positionId) {
		Employee employee = findEmployee(employeeId);
		applyProfile(employee, name, account, phone, email, positionId, employeeId);
		return employeeRepository.save(employee);
	}

	/** 啟用或停用員工帳號，不刪除被其他業務資料引用的員工。 */
	@Transactional
	public Employee updateStatus(Integer employeeId, Integer status) {
		validateStatus(status);
		Employee employee = findEmployee(employeeId);
		employee.setEmployeeStatus(status.byteValue());
		return employeeRepository.save(employee);
	}

	/** 重設密碼時只回傳員工資料，雜湊值仍由 @JsonIgnore 保護。 */
	@Transactional
	public Employee resetPassword(Integer employeeId, String password) {
		validatePassword(password);
		Employee employee = findEmployee(employeeId);
		employee.setEmployeePassword(hashPassword(password));
		return employeeRepository.save(employee);
	}

	/** 取得所有權限功能，依群組與編號排序，供頁面建立分組清單。 */
	public List<EmployeePermissionFunction> findPermissionFunctions() {
		return permissionFunctionRepository.findAllByOrderByPermissionGroupAscPermissionIdAsc();
	}

	/** 取得所有職位，依職位編號排序供清單與員工表單使用。 */
	public List<EmployeePosition> findPositions() {
		return positionRepository.findAllByOrderByPositionIdAsc();
	}

	/** 取得單一職位，不存在時回傳明確錯誤。 */
	public EmployeePosition findPosition(Integer positionId) {
		if (positionId == null) throw new IllegalArgumentException("請選擇員工職位");
		return positionRepository.findById(positionId)
				.orElseThrow(() -> new NoSuchElementException("找不到這個員工職位"));
	}

	/** 新增職位；職位代碼建立後固定，避免程式識別值被任意更改。 */
	@Transactional
	public EmployeePosition createPosition(String code, String name, String description) {
		String cleanCode = requirePositionCode(code);
		String cleanName = requireText(name, "職位名稱不可空白", 50, "職位名稱最多 50 個字");
		if (positionRepository.existsByPositionCodeIgnoreCase(cleanCode)) {
			throw new IllegalArgumentException("此職位代碼已被使用");
		}
		if (positionRepository.existsByPositionNameIgnoreCase(cleanName)) {
			throw new IllegalArgumentException("此職位名稱已被使用");
		}
		EmployeePosition position = new EmployeePosition();
		position.setPositionCode(cleanCode);
		position.setPositionName(cleanName);
		position.setPositionDescription(normalizeDescription(description));
		position.setPositionStatus(STATUS_ACTIVE);
		return positionRepository.save(position);
	}

	/** 修改職位名稱與說明；職位代碼由既有資料保持不變。 */
	@Transactional
	public EmployeePosition updatePosition(Integer positionId, String name, String description) {
		EmployeePosition position = findPosition(positionId);
		String cleanName = requireText(name, "職位名稱不可空白", 50, "職位名稱最多 50 個字");
		if (positionRepository.existsByPositionNameIgnoreCaseAndPositionIdNot(cleanName, positionId)) {
			throw new IllegalArgumentException("此職位名稱已被使用");
		}
		position.setPositionName(cleanName);
		position.setPositionDescription(normalizeDescription(description));
		return positionRepository.save(position);
	}

	/** 啟用或停用職位；已有員工使用的職位不可停用。 */
	@Transactional
	public EmployeePosition updatePositionStatus(Integer positionId, Integer status) {
		validateStatus(status);
		EmployeePosition position = findPosition(positionId);
		if (status == STATUS_DISABLED && employeeRepository.existsByPositionId(positionId)) {
			throw new IllegalArgumentException("仍有員工使用此職位，請先調整員工職位");
		}
		position.setPositionStatus(status.byteValue());
		return positionRepository.save(position);
	}

	/** 取得員工目前擁有的權限編號。 */
	public List<Integer> findEmployeePermissionIds(Integer employeeId) {
		findEmployee(employeeId);
		return employeePermissionRepository.findByEmployeeIdOrderByPermissionIdAsc(employeeId).stream()
				.map(EmployeePermission::getPermissionId)
				.collect(Collectors.toList());
	}

	/** 回傳員工目前的權限數量，供帳號清單快速顯示。 */
	public long countPermissions(Integer employeeId) {
		return employeePermissionRepository.countByEmployeeId(employeeId);
	}

	/**
	 * 一次同步員工權限：移除取消勾選的項目、建立新勾選的項目，整段操作共用同一個交易。
	 */
	@Transactional
	public List<Integer> replaceEmployeePermissions(Integer employeeId, List<Integer> requestedIds) {
		findEmployee(employeeId);
		Set<Integer> targetIds = requestedIds == null ? new LinkedHashSet<>()
				: requestedIds.stream().filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));

		List<EmployeePermissionFunction> functions = permissionFunctionRepository.findAllById(targetIds);
		if (functions.size() != targetIds.size()) {
			throw new IllegalArgumentException("包含不存在的權限功能，請重新整理後再試");
		}

		List<EmployeePermission> current = employeePermissionRepository
				.findByEmployeeIdOrderByPermissionIdAsc(employeeId);
		Set<Integer> currentIds = current.stream().map(EmployeePermission::getPermissionId)
				.collect(Collectors.toCollection(HashSet::new));
		List<EmployeePermission> removed = current.stream()
				.filter(item -> !targetIds.contains(item.getPermissionId()))
				.collect(Collectors.toList());
		List<EmployeePermission> added = new ArrayList<>();
		for (Integer permissionId : targetIds) {
			if (!currentIds.contains(permissionId)) {
				EmployeePermission item = new EmployeePermission();
				item.setEmployeeId(employeeId);
				item.setPermissionId(permissionId);
				added.add(item);
			}
		}

		employeePermissionRepository.deleteAll(removed);
		employeePermissionRepository.saveAll(added);
		return targetIds.stream().sorted().collect(Collectors.toList());
	}

	/** 修改權限顯示名稱、說明與群組；程式使用的權限代碼保持不變。 */
	@Transactional
	public EmployeePermissionFunction updatePermissionFunction(Integer permissionId,
			String name, String description, String group) {
		EmployeePermissionFunction function = permissionFunctionRepository.findById(permissionId)
				.orElseThrow(() -> new NoSuchElementException("找不到這個權限功能"));
		function.setPermissionName(requireText(name, "權限名稱不可空白", 100, "權限名稱最多 100 個字"));
		function.setPermissionDescription(normalizeOptional(description, 255, "權限說明最多 255 個字"));
		function.setPermissionGroup(requireText(group, "權限群組不可空白", 50, "權限群組最多 50 個字"));
		return permissionFunctionRepository.save(function);
	}

	private void applyProfile(Employee employee, String name, String account, String phone,
			String email, Integer positionId, Integer currentEmployeeId) {
		String cleanName = requireText(name, "員工姓名不可空白", 50, "員工姓名最多 50 個字");
		String cleanAccount = requireText(account, "員工帳號不可空白", 50, "員工帳號最多 50 個字");
		String cleanPhone = requireText(phone, "聯絡電話不可空白", 20, "聯絡電話最多 20 個字");
		String cleanEmail = requireText(email, "電子信箱不可空白", 100, "電子信箱最多 100 個字")
				.toLowerCase(Locale.ROOT);

		if (!ACCOUNT_PATTERN.matcher(cleanAccount).matches()) {
			throw new IllegalArgumentException("員工帳號需為 4～50 個英文字母、數字或底線");
		}
		if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
			throw new IllegalArgumentException("聯絡電話格式不正確");
		}
		if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
			throw new IllegalArgumentException("電子信箱格式不正確");
		}
		EmployeePosition position = findPosition(positionId);
		if (position.getPositionStatus() == null || position.getPositionStatus() != STATUS_ACTIVE) {
			throw new IllegalArgumentException("請選擇啟用中的員工職位");
		}

		boolean duplicateAccount = currentEmployeeId == null
				? employeeRepository.existsByEmployeeAccountIgnoreCase(cleanAccount)
				: employeeRepository.existsByEmployeeAccountIgnoreCaseAndEmployeeIdNot(cleanAccount, currentEmployeeId);
		if (duplicateAccount) {
			throw new IllegalArgumentException("員工帳號【" + cleanAccount + "】已被使用，請更換其他帳號");
		}

		boolean duplicateEmail = currentEmployeeId == null
				? employeeRepository.existsByEmployeeEmailIgnoreCase(cleanEmail)
				: employeeRepository.existsByEmployeeEmailIgnoreCaseAndEmployeeIdNot(cleanEmail, currentEmployeeId);
		if (duplicateEmail) {
			throw new IllegalArgumentException("電子信箱【" + cleanEmail + "】已被使用，請更換其他信箱");
		}

		boolean duplicatePhone = currentEmployeeId == null
				? employeeRepository.existsByEmployeePhone(cleanPhone)
				: employeeRepository.existsByEmployeePhoneAndEmployeeIdNot(cleanPhone, currentEmployeeId);
		if (duplicatePhone) {
			throw new IllegalArgumentException("聯絡電話【" + cleanPhone + "】已被使用，請更換其他電話");
		}

		employee.setEmployeeName(cleanName);
		employee.setEmployeeAccount(cleanAccount);
		employee.setEmployeePhone(cleanPhone);
		employee.setEmployeeEmail(cleanEmail);
		employee.setPositionId(position.getPositionId());
	}

	private String requirePositionCode(String value) {
		String cleanCode = requireText(value, "職位代碼不可空白", 30, "職位代碼最多 30 個字")
				.toUpperCase(Locale.ROOT);
		if (!cleanCode.matches("^[A-Z][A-Z0-9_]{1,29}$")) {
			throw new IllegalArgumentException("職位代碼需為 2～30 個大寫英文字母、數字或底線");
		}
		return cleanCode;
	}

	private String normalizeDescription(String value) {
		String description = normalizeOptional(value, 255, "職位說明最多 255 個字");
		return description == null ? "" : description;
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 8 || password.length() > 72) {
			throw new IllegalArgumentException("密碼需為 8～72 個字元");
		}
	}

	private void validateStatus(Integer status) {
		if (status == null || (status != STATUS_DISABLED && status != STATUS_ACTIVE)) {
			throw new IllegalArgumentException("員工狀態只能是 0（停用）或 1（正常）");
		}
	}

	private boolean contains(String value, String keyword) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
	}

	private String requireText(String value, String blankMessage, int maxLength, String lengthMessage) {
		if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(blankMessage);
		String cleanValue = value.trim();
		if (cleanValue.length() > maxLength) throw new IllegalArgumentException(lengthMessage);
		return cleanValue;
	}

	private String normalizeOptional(String value, int maxLength, String lengthMessage) {
		if (value == null || value.trim().isEmpty()) return null;
		String cleanValue = value.trim();
		if (cleanValue.length() > maxLength) throw new IllegalArgumentException(lengthMessage);
		return cleanValue;
	}

	/** 使用隨機鹽值與 PBKDF2 保存新密碼，資料庫不會出現新的明文密碼。 */
	private String hashPassword(String password) {
		byte[] salt = new byte[PASSWORD_SALT_LENGTH];
		new SecureRandom().nextBytes(salt);
		PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt,
				PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
		try {
			byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
					.generateSecret(specification).getEncoded();
			return "pbkdf2$" + PASSWORD_ITERATIONS + "$"
					+ Base64.getEncoder().encodeToString(salt) + "$"
					+ Base64.getEncoder().encodeToString(hash);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException error) {
			throw new IllegalStateException("目前環境不支援 PBKDF2 密碼雜湊", error);
		} finally {
			specification.clearPassword();
		}
	}

	/** 驗證 PBKDF2 密碼；舊資料若仍是明文，登入後仍可先使用再改成雜湊密碼。 */
	private boolean passwordsMatch(String rawPassword, String storedPassword) {
		if (rawPassword == null || storedPassword == null) return false;
		if (!storedPassword.startsWith("pbkdf2$")) {
			return MessageDigest.isEqual(rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
					storedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		try {
			String[] parts = storedPassword.split("\\$", -1);
			if (parts.length != 4) return false;
			int iterations = Integer.parseInt(parts[1]);
			byte[] salt = Base64.getDecoder().decode(parts[2]);
			byte[] expected = Base64.getDecoder().decode(parts[3]);
			PBEKeySpec specification = new PBEKeySpec(rawPassword.toCharArray(), salt,
					iterations, expected.length * 8);
			try {
				byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
						.generateSecret(specification).getEncoded();
				return MessageDigest.isEqual(expected, actual);
			} finally {
				specification.clearPassword();
			}
		} catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException error) {
			return false;
		}
	}
}
