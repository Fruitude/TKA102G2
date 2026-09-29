package com.fruitude.member.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.MemberService;
import com.fruitude.member.model.MemberVO;

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
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(memberService.register(member));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message(e.getMessage()));
		}
	}

	/** 使用帳號或 Email 加密碼登入。 */
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestParam("account") String accountOrEmail,
			@RequestParam("password") String password) {
		Optional<MemberVO> member = memberService.login(accountOrEmail, password);
		if (!member.isPresent()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("帳號、密碼錯誤或會員已停權"));
		}
		return ResponseEntity.ok(member.get());
	}

	/** 依會員編號查詢個人資料。 */
	@GetMapping("/{memberId}")
	public ResponseEntity<?> getProfile(@PathVariable Integer memberId) {
		Optional<MemberVO> member = memberService.findById(memberId);
		return member.isPresent()
				? ResponseEntity.ok(member.get())
				: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
	}

	/** 修改可由會員自行維護的個人資料。 */
	@PutMapping("/{memberId}")
	public ResponseEntity<?> updateProfile(@PathVariable Integer memberId, @RequestBody MemberVO form) {
		try {
			Optional<MemberVO> member = memberService.updateProfile(memberId, form);
			return member.isPresent()
					? ResponseEntity.ok(member.get())
					: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(message(e.getMessage()));
		}
	}

	// 統一錯誤訊息的 JSON 格式，例如：{"message":"找不到會員"}。
	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}
}
