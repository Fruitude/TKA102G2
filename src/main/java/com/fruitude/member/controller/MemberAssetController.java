package com.fruitude.member.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.MemberAssetService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 登入會員的地址、電話及信用卡 API。所有操作都以 session 會員編號為準，不能指定別人的會員編號。
 */
@RestController
@RequestMapping("/api/members/me")
public class MemberAssetController {

	private final MemberAssetService memberAssetService;

	public MemberAssetController(MemberAssetService memberAssetService) {
		this.memberAssetService = memberAssetService;
	}

	/** 取得目前登入會員的地址清單。 */
	@GetMapping("/addresses")
	public ResponseEntity<?> getAddresses(HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(memberAssetService.findAddresses(memberId));
	}

	/** 新增目前登入會員的地址。 */
	@PostMapping("/addresses")
	public ResponseEntity<?> createAddress(@RequestBody AddressForm form, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.status(HttpStatus.CREATED)
				.body(memberAssetService.createAddress(memberId, form.getContactAddress(), form.isMakeDefault()));
	}

	/** 修改目前登入會員自己的地址。 */
	@PutMapping("/addresses/{addressId}")
	public ResponseEntity<?> updateAddress(@PathVariable("addressId") Integer addressId,
			@RequestBody AddressForm form, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(memberAssetService.updateAddress(
				memberId, addressId, form.getContactAddress(), form.isMakeDefault()));
	}

	/** 刪除目前登入會員自己的地址。 */
	@DeleteMapping("/addresses/{addressId}")
	public ResponseEntity<?> deleteAddress(@PathVariable("addressId") Integer addressId, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		if (memberId == null) return unauthorized();
		memberAssetService.deleteAddress(memberId, addressId);
		return ResponseEntity.noContent().build();
	}

	/** 取得目前登入會員的電話清單。 */
	@GetMapping("/phones")
	public ResponseEntity<?> getPhones(HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(memberAssetService.findPhones(memberId));
	}

	/** 新增目前登入會員的電話。 */
	@PostMapping("/phones")
	public ResponseEntity<?> createPhone(@RequestBody PhoneForm form, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.status(HttpStatus.CREATED)
				.body(memberAssetService.createPhone(memberId, form.getContactPhone(), form.isMakeDefault()));
	}

	/** 修改目前登入會員自己的電話。 */
	@PutMapping("/phones/{phoneId}")
	public ResponseEntity<?> updatePhone(@PathVariable("phoneId") Integer phoneId,
			@RequestBody PhoneForm form, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(memberAssetService.updatePhone(
				memberId, phoneId, form.getContactPhone(), form.isMakeDefault()));
	}

	/** 刪除目前登入會員自己的電話。 */
	@DeleteMapping("/phones/{phoneId}")
	public ResponseEntity<?> deletePhone(@PathVariable("phoneId") Integer phoneId, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		if (memberId == null) return unauthorized();
		memberAssetService.deletePhone(memberId, phoneId);
		return ResponseEntity.noContent().build();
	}

	/** 取得目前登入會員的信用卡安全摘要，不包含 token 或完整卡號。 */
	@GetMapping("/credit-cards")
	public ResponseEntity<?> getCreditCards(HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(memberAssetService.findCreditCards(memberId));
	}

	/** 新增信用卡安全摘要，伺服器只接收末四碼並自行產生 token。 */
	@PostMapping("/credit-cards")
	public ResponseEntity<?> createCreditCard(@RequestBody CreditCardForm form, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.status(HttpStatus.CREATED)
				.body(memberAssetService.createCreditCard(memberId, form.getCardBrand(), form.getCardLastFour(),
						form.getCardholderName(), form.getExpiryYearMonth(), form.isMakeDefault()));
	}

	/** 修改目前登入會員自己的信用卡安全摘要。 */
	@PutMapping("/credit-cards/{creditCardId}")
	public ResponseEntity<?> updateCreditCard(@PathVariable("creditCardId") Integer creditCardId,
			@RequestBody CreditCardForm form, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		return memberId == null ? unauthorized() : ResponseEntity.ok(memberAssetService.updateCreditCard(
				memberId, creditCardId, form.getCardBrand(), form.getCardLastFour(), form.getCardholderName(),
				form.getExpiryYearMonth(), form.isMakeDefault()));
	}

	/** 刪除目前登入會員自己的信用卡安全摘要。 */
	@DeleteMapping("/credit-cards/{creditCardId}")
	public ResponseEntity<?> deleteCreditCard(@PathVariable("creditCardId") Integer creditCardId, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		if (memberId == null) return unauthorized();
		memberAssetService.deleteCreditCard(memberId, creditCardId);
		return ResponseEntity.noContent().build();
	}

	/** 將欄位格式錯誤統一轉成 400，讓頁面可以顯示清楚的行內訊息。 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException error) {
		return ResponseEntity.badRequest().body(message(error.getMessage()));
	}

	/** 資料不存在或不屬於目前會員時一律回傳 404，不洩漏其他會員是否有這筆資料。 */
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException error) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(error.getMessage()));
	}

	// 登入時 MemberController 已把會員編號放入 session，這裡只讀取該值。
	private Integer getLoggedInMemberId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute("loggedInMemberId") instanceof Integer)) {
			return null;
		}
		return (Integer) session.getAttribute("loggedInMemberId");
	}

	private ResponseEntity<Map<String, String>> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("請先登入會員"));
	}

	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 地址新增／修改表單，只接受地址內容及是否設為預設。 */
	public static class AddressForm {
		private String contactAddress;
		private boolean makeDefault;

		public String getContactAddress() { return contactAddress; }
		public void setContactAddress(String contactAddress) { this.contactAddress = contactAddress; }
		public boolean isMakeDefault() { return makeDefault; }
		public void setMakeDefault(boolean makeDefault) { this.makeDefault = makeDefault; }
	}

	/** 電話新增／修改表單，只接受聯絡電話及是否設為預設。 */
	public static class PhoneForm {
		private String contactPhone;
		private boolean makeDefault;

		public String getContactPhone() { return contactPhone; }
		public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
		public boolean isMakeDefault() { return makeDefault; }
		public void setMakeDefault(boolean makeDefault) { this.makeDefault = makeDefault; }
	}

	/** 信用卡表單刻意不含完整卡號及 token，避免敏感資料進入前端流程。 */
	public static class CreditCardForm {
		private String cardBrand;
		private String cardLastFour;
		private String cardholderName;
		private String expiryYearMonth;
		private boolean makeDefault;

		public String getCardBrand() { return cardBrand; }
		public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
		public String getCardLastFour() { return cardLastFour; }
		public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
		public String getCardholderName() { return cardholderName; }
		public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }
		public String getExpiryYearMonth() { return expiryYearMonth; }
		public void setExpiryYearMonth(String expiryYearMonth) { this.expiryYearMonth = expiryYearMonth; }
		public boolean isMakeDefault() { return makeDefault; }
		public void setMakeDefault(boolean makeDefault) { this.makeDefault = makeDefault; }
	}
}
