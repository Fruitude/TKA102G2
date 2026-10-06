package com.fruitude.member.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.MemberService;
import com.fruitude.member.model.MemberVO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * 前台會員 API，處理註冊、登入、查看個人資料與修改個人資料。
 */
@RestController
@RequestMapping("/api/members")
@Validated
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	/** 接收 JSON 會員資料並建立新會員。 */
	@PostMapping("/register")
	public ResponseEntity<?> register(@Valid @RequestBody MemberVO member) {
		// 格式錯誤屬於請求內容問題；帳號或 Email 重複則由下方回傳 409 衝突。
		if (!memberService.isRegistrationAccountValid(member.getMemberAccount())) {
			return ResponseEntity.badRequest().body(message("會員帳號需為 6～20 個英文字母、數字或底線"));
		}
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(memberService.register(member));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message(e.getMessage()));
		}
	}

	/** 註冊頁輸入帳號後，即時回傳格式是否正確及帳號是否可以使用。 */
	@GetMapping("/availability/account")
	public ResponseEntity<Map<String, Object>> checkAccountAvailability(@RequestParam("value") String account) {
		boolean valid = memberService.isRegistrationAccountValid(account);
		boolean available = valid && memberService.isAccountAvailable(account);
		String text = !valid ? "帳號需為 6～20 個英文字母、數字或底線"
				: available ? "此帳號可以使用" : "此帳號已被使用";
		return ResponseEntity.ok(availability(valid, available, text));
	}

	/** 註冊頁輸入 Email 後，即時回傳格式是否正確及 Email 是否可以使用。 */
	@GetMapping("/availability/email")
	public ResponseEntity<Map<String, Object>> checkEmailAvailability(@RequestParam("value") String email) {
		boolean valid = memberService.isRegistrationEmailValid(email);
		boolean available = valid && memberService.isEmailAvailable(email);
		String text = !valid ? "電子郵件格式不正確"
				: available ? "此電子郵件可以使用" : "此電子郵件已被註冊";
		return ResponseEntity.ok(availability(valid, available, text));
	}

	/** 使用帳號或 Email 加密碼登入。 */
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestParam("account") String accountOrEmail,
			@RequestParam("password") String password, HttpServletRequest request) {
		try {
			MemberVO member = memberService.loginOrThrow(accountOrEmail, password);
			// 登入成功後更新 session ID，並只保存後續頁面需要的會員識別資料。
			HttpSession session = request.getSession();
			request.changeSessionId();
			session.setAttribute("loggedInMemberId", member.getMemberId());
			session.setAttribute("loggedInMemberName", member.getMemberName());
			return ResponseEntity.ok(member);
		} catch (IllegalArgumentException error) {
			// 將查無會員、密碼錯誤及停權分別說明，避免使用者無法判斷問題。
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message(error.getMessage()));
		}
	}

	/** 讓前端確認目前瀏覽器是否已有登入中的會員 session。 */
	@GetMapping("/session")
	public ResponseEntity<Map<String, Object>> getLoginStatus(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		Map<String, Object> body = new LinkedHashMap<>();
		boolean loggedIn = session != null && session.getAttribute("loggedInMemberId") != null;
		body.put("loggedIn", loggedIn);
		if (loggedIn) {
			body.put("memberId", session.getAttribute("loggedInMemberId"));
			body.put("memberName", session.getAttribute("loggedInMemberName"));
		}
		return ResponseEntity.ok(body);
	}

	/** 依目前登入 session 取得會員自己的完整資料，避免前端自行指定其他會員編號。 */
	@GetMapping("/me")
	public ResponseEntity<?> getMyProfile(HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		if (memberId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("請先登入會員"));
		}

		Optional<MemberVO> member = memberService.findById(memberId);
		return member.isPresent()
				? ResponseEntity.ok(member.get())
				: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
	}

	/** 修改目前登入會員可自行維護的姓名、生日、帳號、Email 與密碼。 */
	@PutMapping("/me")
	public ResponseEntity<?> updateMyProfile(@RequestBody MemberVO form, HttpServletRequest request) {
		Integer memberId = getLoggedInMemberId(request);
		if (memberId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("請先登入會員"));
		}

		try {
			Optional<MemberVO> member = memberService.updateProfile(memberId, form);
			if (!member.isPresent()) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
			}

			// 姓名可能已被修改，同步更新導覽列會使用的 session 資料。
			request.getSession(false).setAttribute("loggedInMemberName", member.get().getMemberName());
			return ResponseEntity.ok(member.get());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message(e.getMessage()));
		}
	}

	/** 登出時使整個會員 session 失效。 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return ResponseEntity.noContent().build();
	}

	// 集中讀取 session 內的會員編號，沒有登入時回傳 null。
	private Integer getLoggedInMemberId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute("loggedInMemberId") instanceof Integer)) {
			return null;
		}
		return (Integer) session.getAttribute("loggedInMemberId");
	}

	// 統一錯誤訊息的 JSON 格式，例如：{"message":"找不到會員"}。
	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	// 統一帳號與 Email 即時檢查的 JSON 格式，讓前端能用相同流程顯示狀態。
	private Map<String, Object> availability(boolean valid, boolean available, String text) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("valid", valid);
		body.put("available", available);
		body.put("message", text);
		return body;
	}
}
