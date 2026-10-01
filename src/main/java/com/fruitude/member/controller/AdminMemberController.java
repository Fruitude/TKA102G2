package com.fruitude.member.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.MemberService;
import com.fruitude.member.model.MemberVO;

/**
 * 後台會員管理 API，提供清單、搜尋、狀態、購物金及刪除功能。
 */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

	private final MemberService memberService;

	public AdminMemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	/**
	 * 會員清單：可傳 keyword 搜尋，或傳 status 篩選；兩者都沒傳時回傳全部會員。
	 */
	@GetMapping
	public ResponseEntity<?> getMembers(
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "status", required = false) Integer status) {
		try {
			List<MemberVO> members;
			if (keyword != null && !keyword.trim().isEmpty()) {
				members = memberService.search(keyword);
			} else if (status != null) {
				members = memberService.findByStatus(status);
			} else {
				members = memberService.findAll();
			}
			return ResponseEntity.ok(members);
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
			@RequestParam("value") Integer status) {
		try {
			Optional<MemberVO> member = memberService.updateStatus(memberId, status);
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
			@RequestParam("value") Integer shoppingCredit) {
		try {
			Optional<MemberVO> member = memberService.updateShoppingCredit(memberId, shoppingCredit);
			return member.isPresent()
					? ResponseEntity.ok(member.get())
					: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 刪除指定會員。 */
	@DeleteMapping("/{memberId}")
	public ResponseEntity<?> deleteMember(@PathVariable Integer memberId) {
		if (!memberService.delete(memberId)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
		}
		return ResponseEntity.noContent().build();
	}

	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}
}
