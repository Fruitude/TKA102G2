package com.fruitude.member.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import com.fruitude.member.model.MemberCreditTransactionDTO;

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
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.member.model.MemberService;
import com.fruitude.member.model.OnlineMemberTracker;
import com.fruitude.member.model.MemberCreditTransaction;
import com.fruitude.member.model.MemberVO;
import com.fruitude.employee.model.OperationAuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 後台會員管理 API，提供分頁清單、搜尋、狀態與購物金管理功能。
 */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

	private final MemberService memberService;
	private final OperationAuditService auditService;
	private final OnlineMemberTracker onlineMemberTracker;

	public AdminMemberController(MemberService memberService, OperationAuditService auditService,
			OnlineMemberTracker onlineMemberTracker) {
		this.memberService = memberService;
		this.auditService = auditService;
		this.onlineMemberTracker = onlineMemberTracker;
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
			body.put("onlineCount", onlineMemberTracker.countOnlineMembers());
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
	@Transactional
	@PatchMapping("/{memberId}/status")
	public ResponseEntity<?> updateStatus(@PathVariable Integer memberId,
			@RequestBody StatusRequest form, HttpServletRequest request) {
		try {
			Optional<MemberVO> member = memberService.updateStatus(memberId, form.getMemberStatus());
			return member.isPresent()
					? ResponseEntity.ok(member.get())
					: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 設定會員目前的購物金餘額。 */
	@Transactional
	@PatchMapping("/{memberId}/credit")
	public ResponseEntity<?> updateShoppingCredit(@PathVariable Integer memberId,
			@RequestBody CreditRequest form, HttpServletRequest request) {
		try {
			Optional<MemberVO> member = memberService.updateShoppingCredit(memberId, form.getShoppingCredit(),
					form.getReason(), auditService.resolveEmployeeId(request));
			return member.isPresent()
					? ResponseEntity.ok(member.get())
					: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 集中分頁查詢所有會員的購物金異動紀錄。 */
	@GetMapping("/credit-transactions")
	public ResponseEntity<?> getAllCreditTransactions(
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "type", required = false) Byte type,
			@RequestParam(value = "startDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(value = "endDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		try {
			Page<MemberCreditTransactionDTO> transactions = memberService.findAllCreditTransactions(
					keyword, type, startDate, endDate, page, size);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("transactions", transactions.getContent());
			body.put("page", transactions.getNumber());
			body.put("size", transactions.getSize());
			body.put("totalPages", transactions.getTotalPages());
			body.put("totalElements", transactions.getTotalElements());
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 分頁取得指定會員的購物金異動紀錄。 */
	@GetMapping("/{memberId}/credit-transactions")
	public ResponseEntity<?> getCreditTransactions(@PathVariable Integer memberId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		try {
			Page<MemberCreditTransaction> transactions = memberService.findCreditTransactions(memberId, page, size);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("transactions", transactions.getContent());
			body.put("page", transactions.getNumber());
			body.put("totalPages", transactions.getTotalPages());
			body.put("totalElements", transactions.getTotalElements());
			return ResponseEntity.ok(body);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));
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
		private String reason;

		public Integer getShoppingCredit() {
			return shoppingCredit;
		}

		public void setShoppingCredit(Integer shoppingCredit) {
			this.shoppingCredit = shoppingCredit;
		}

		public String getReason() { return reason; }
		public void setReason(String reason) { this.reason = reason; }
	}
}
