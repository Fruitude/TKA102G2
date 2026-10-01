package com.fruitude.member.model;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 會員聯絡資料服務層：集中處理地址、電話、信用卡及「只能有一筆預設資料」的規則。
 */
@Service
@Transactional(readOnly = true)
public class MemberAssetService {

	private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\- ]{6,20}$");
	private static final Pattern FOUR_DIGITS_PATTERN = Pattern.compile("^\\d{4}$");
	private static final Pattern EXPIRY_PATTERN = Pattern.compile("^\\d{2}(0[1-9]|1[0-2])$");

	private final MemberAddressRepository addressRepository;
	private final MemberPhoneRepository phoneRepository;
	private final MemberCreditCardRepository creditCardRepository;

	/** 建構子注入三個 Repository，方便測試並確保服務建立時依賴完整。 */
	public MemberAssetService(MemberAddressRepository addressRepository,
			MemberPhoneRepository phoneRepository,
			MemberCreditCardRepository creditCardRepository) {
		this.addressRepository = addressRepository;
		this.phoneRepository = phoneRepository;
		this.creditCardRepository = creditCardRepository;
	}

	/** 讀取目前會員的地址，預設地址排在最前面。 */
	public List<MemberAddress> findAddresses(Integer memberId) {
		return addressRepository.findByMemberIdOrderByDefaultValueDescAddressIdAsc(memberId);
	}

	/** 新增地址；第一筆地址一定會自動成為預設地址。 */
	@Transactional
	public MemberAddress createAddress(Integer memberId, String address, boolean makeDefault) {
		String cleanAddress = requireText(address, "地址不可空白", 255, "地址最多 255 個字");
		List<MemberAddress> current = findAddresses(memberId);
		boolean shouldBeDefault = current.isEmpty() || makeDefault;
		if (shouldBeDefault) {
			clearAddressDefaults(current);
		}

		MemberAddress item = new MemberAddress();
		item.setMemberId(memberId);
		item.setContactAddress(cleanAddress);
		item.setDefaultValue((byte) (shouldBeDefault ? 1 : 0));
		return addressRepository.save(item);
	}

	/** 修改本人地址；查詢時同時比對會員編號，避免修改到別人的資料。 */
	@Transactional
	public MemberAddress updateAddress(Integer memberId, Integer addressId, String address, boolean makeDefault) {
		MemberAddress item = addressRepository.findByAddressIdAndMemberId(addressId, memberId)
				.orElseThrow(() -> new NoSuchElementException("找不到這筆地址"));
		item.setContactAddress(requireText(address, "地址不可空白", 255, "地址最多 255 個字"));
		if (makeDefault) {
			clearAddressDefaults(findAddresses(memberId));
			item.setDefaultValue((byte) 1);
		}
		return addressRepository.save(item);
	}

	/** 刪除本人地址；若刪除預設地址，會把剩下的第一筆補為預設。 */
	@Transactional
	public void deleteAddress(Integer memberId, Integer addressId) {
		MemberAddress item = addressRepository.findByAddressIdAndMemberId(addressId, memberId)
				.orElseThrow(() -> new NoSuchElementException("找不到這筆地址"));
		boolean wasDefault = Byte.valueOf((byte) 1).equals(item.getDefaultValue());
		addressRepository.delete(item);
		addressRepository.flush();
		if (wasDefault) {
			promoteFirstAddress(memberId);
		}
	}

	/** 讀取目前會員的電話，預設電話排在最前面。 */
	public List<MemberPhone> findPhones(Integer memberId) {
		return phoneRepository.findByMemberIdOrderByDefaultValueDescPhoneIdAsc(memberId);
	}

	/** 新增電話並檢查可接受的電話字元與長度。 */
	@Transactional
	public MemberPhone createPhone(Integer memberId, String phone, boolean makeDefault) {
		String cleanPhone = validatePhone(phone);
		List<MemberPhone> current = findPhones(memberId);
		boolean shouldBeDefault = current.isEmpty() || makeDefault;
		if (shouldBeDefault) {
			clearPhoneDefaults(current);
		}

		MemberPhone item = new MemberPhone();
		item.setMemberId(memberId);
		item.setContactPhone(cleanPhone);
		item.setDefaultValue((byte) (shouldBeDefault ? 1 : 0));
		return phoneRepository.save(item);
	}

	/** 修改本人電話，並可把這筆資料設為新的預設電話。 */
	@Transactional
	public MemberPhone updatePhone(Integer memberId, Integer phoneId, String phone, boolean makeDefault) {
		MemberPhone item = phoneRepository.findByPhoneIdAndMemberId(phoneId, memberId)
				.orElseThrow(() -> new NoSuchElementException("找不到這筆電話"));
		item.setContactPhone(validatePhone(phone));
		if (makeDefault) {
			clearPhoneDefaults(findPhones(memberId));
			item.setDefaultValue((byte) 1);
		}
		return phoneRepository.save(item);
	}

	/** 刪除本人電話，必要時自動補上一筆預設電話。 */
	@Transactional
	public void deletePhone(Integer memberId, Integer phoneId) {
		MemberPhone item = phoneRepository.findByPhoneIdAndMemberId(phoneId, memberId)
				.orElseThrow(() -> new NoSuchElementException("找不到這筆電話"));
		boolean wasDefault = Byte.valueOf((byte) 1).equals(item.getDefaultValue());
		phoneRepository.delete(item);
		phoneRepository.flush();
		if (wasDefault) {
			promoteFirstPhone(memberId);
		}
	}

	/** 讀取目前會員的信用卡安全摘要，cardToken 已由實體設定為不輸出 JSON。 */
	public List<MemberCreditCard> findCreditCards(Integer memberId) {
		return creditCardRepository.findByMemberIdOrderByDefaultValueDescCreditCardIdAsc(memberId);
	}

	/**
	 * 新增信用卡摘要。系統只接收末四碼，不接收完整卡號，並在伺服器產生不可預測的 token。
	 */
	@Transactional
	public MemberCreditCard createCreditCard(Integer memberId, String brand, String lastFour,
			String cardholderName, String expiryYearMonth, boolean makeDefault) {
		validateCard(lastFour, expiryYearMonth);
		List<MemberCreditCard> current = findCreditCards(memberId);
		boolean shouldBeDefault = current.isEmpty() || makeDefault;
		if (shouldBeDefault) {
			clearCreditCardDefaults(current);
		}

		MemberCreditCard item = new MemberCreditCard();
		item.setMemberId(memberId);
		item.setCardToken(UUID.randomUUID().toString());
		item.setCardBrand(requireText(brand, "信用卡品牌不可空白", 20, "信用卡品牌最多 20 個字"));
		item.setCardLastFour(lastFour.trim());
		item.setCardholderName(requireText(cardholderName, "持卡人姓名不可空白", 50, "持卡人姓名最多 50 個字"));
		item.setExpiryYearMonth(expiryYearMonth.trim());
		item.setDefaultValue((byte) (shouldBeDefault ? 1 : 0));
		return creditCardRepository.save(item);
	}

	/** 修改本人信用卡的安全摘要；原有 token 保持不變。 */
	@Transactional
	public MemberCreditCard updateCreditCard(Integer memberId, Integer creditCardId, String brand,
			String lastFour, String cardholderName, String expiryYearMonth, boolean makeDefault) {
		MemberCreditCard item = creditCardRepository.findByCreditCardIdAndMemberId(creditCardId, memberId)
				.orElseThrow(() -> new NoSuchElementException("找不到這張信用卡"));
		validateCard(lastFour, expiryYearMonth);
		item.setCardBrand(requireText(brand, "信用卡品牌不可空白", 20, "信用卡品牌最多 20 個字"));
		item.setCardLastFour(lastFour.trim());
		item.setCardholderName(requireText(cardholderName, "持卡人姓名不可空白", 50, "持卡人姓名最多 50 個字"));
		item.setExpiryYearMonth(expiryYearMonth.trim());
		if (makeDefault) {
			clearCreditCardDefaults(findCreditCards(memberId));
			item.setDefaultValue((byte) 1);
		}
		return creditCardRepository.save(item);
	}

	/** 刪除本人信用卡摘要，必要時自動補上一張預設卡。 */
	@Transactional
	public void deleteCreditCard(Integer memberId, Integer creditCardId) {
		MemberCreditCard item = creditCardRepository.findByCreditCardIdAndMemberId(creditCardId, memberId)
				.orElseThrow(() -> new NoSuchElementException("找不到這張信用卡"));
		boolean wasDefault = Byte.valueOf((byte) 1).equals(item.getDefaultValue());
		creditCardRepository.delete(item);
		creditCardRepository.flush();
		if (wasDefault) {
			promoteFirstCreditCard(memberId);
		}
	}

	// 設定新預設資料前，先把該會員原本的預設資料全部取消。
	private void clearAddressDefaults(List<MemberAddress> items) {
		items.forEach(item -> item.setDefaultValue((byte) 0));
		addressRepository.saveAll(items);
	}

	private void clearPhoneDefaults(List<MemberPhone> items) {
		items.forEach(item -> item.setDefaultValue((byte) 0));
		phoneRepository.saveAll(items);
	}

	private void clearCreditCardDefaults(List<MemberCreditCard> items) {
		items.forEach(item -> item.setDefaultValue((byte) 0));
		creditCardRepository.saveAll(items);
	}

	// 預設資料被刪除後，若仍有其他資料，就依編號把第一筆設為預設。
	private void promoteFirstAddress(Integer memberId) {
		List<MemberAddress> items = findAddresses(memberId);
		if (!items.isEmpty()) {
			items.get(0).setDefaultValue((byte) 1);
			addressRepository.save(items.get(0));
		}
	}

	private void promoteFirstPhone(Integer memberId) {
		List<MemberPhone> items = findPhones(memberId);
		if (!items.isEmpty()) {
			items.get(0).setDefaultValue((byte) 1);
			phoneRepository.save(items.get(0));
		}
	}

	private void promoteFirstCreditCard(Integer memberId) {
		List<MemberCreditCard> items = findCreditCards(memberId);
		if (!items.isEmpty()) {
			items.get(0).setDefaultValue((byte) 1);
			creditCardRepository.save(items.get(0));
		}
	}

	private String validatePhone(String phone) {
		String cleanPhone = requireText(phone, "電話不可空白", 20, "電話最多 20 個字");
		if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
			throw new IllegalArgumentException("電話格式不正確，請輸入 6～20 碼電話號碼");
		}
		return cleanPhone;
	}

	private void validateCard(String lastFour, String expiryYearMonth) {
		if (lastFour == null || !FOUR_DIGITS_PATTERN.matcher(lastFour.trim()).matches()) {
			throw new IllegalArgumentException("信用卡末四碼必須是 4 位數字");
		}
		if (expiryYearMonth == null || !EXPIRY_PATTERN.matcher(expiryYearMonth.trim()).matches()) {
			throw new IllegalArgumentException("到期年月請輸入 YYMM，例如 2912");
		}
	}

	private String requireText(String value, String blankMessage, int maxLength, String lengthMessage) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(blankMessage);
		}
		String cleanValue = value.trim();
		if (cleanValue.length() > maxLength) {
			throw new IllegalArgumentException(lengthMessage);
		}
		return cleanValue;
	}
}
