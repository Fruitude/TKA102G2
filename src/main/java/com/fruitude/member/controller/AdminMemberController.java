package com.fruitude.member.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.MemberService;
import com.fruitude.member.model.MemberVO;

/**
 * 後台會員管理 API，提供分頁清單、搜尋、狀態與購物金管理功能。
 */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

	private final MemberService memberService;

	public AdminMemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	/**
	 * 會員清單：關鍵字與狀態可同時使用，並以資料庫分頁回傳。
	 */
	@GetMapping
	public ResponseEntity<?> getMembers(
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "status", required = false) Integer status,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		try {
			Page<MemberVO> members = memberService.findAdminPage(keyword, status, page, size);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("members", members.getContent());
			body.put("page", members.getNumber());
			body.put("size", members.getSize());
			body.put("totalElements", members.getTotalElements());
			body.put("totalPages", members.getTotalPages());
			body.put("first", members.isFirst());
			body.put("last", members.isLast());
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 依會員編號查看後台會員明細。 */
	@GetMapping("/{memberId}")
	public ResponseEntity<?> getMember(@PathVariable Integer memberId) {
		Optional<MemberVO> member = memberService.findById(memberId);
		return member.isPresent()
				? ResponseEntity.ok(member.get())
				: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
	}

	/** 將會員設為啟用（1）或停權（0）。 */
	@PatchMapping("/{memberId}/status")
	public ResponseEntity<?> updateStatus(@PathVariable Integer memberId,
			@RequestBody StatusRequest request) {
		try {
			Optional<MemberVO> member = memberService.updateStatus(memberId, request.getMemberStatus());
			return member.isPresent()
					? ResponseEntity.ok(member.get())
					: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 設定會員目前的購物金餘額。 */
	@PatchMapping("/{memberId}/credit")
	public ResponseEntity<?> updateShoppingCredit(@PathVariable Integer memberId,
			@RequestBody CreditRequest request) {
		try {
			Optional<MemberVO> member = memberService.updateShoppingCredit(memberId, request.getShoppingCredit());
			return member.isPresent()
					? ResponseEntity.ok(member.get())
					: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 後台狀態修改請求，只接受 memberStatus 欄位。 */
	public static class StatusRequest {
		private Integer memberStatus;

		public Integer getMemberStatus() {
			return memberStatus;
		}

		public void setMemberStatus(Integer memberStatus) {
			this.memberStatus = memberStatus;
		}
	}

	/** 後台購物金修改請求，以新的購物金餘額覆蓋原值。 */
	public static class CreditRequest {
		private Integer shoppingCredit;

		public Integer getShoppingCredit() {
			return shoppingCredit;
		}

		public void setShoppingCredit(Integer shoppingCredit) {
			this.shoppingCredit = shoppingCredit;
		}
	}
}
