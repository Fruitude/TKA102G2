package com.fruitude.member.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.member.model.AdminMemberMutationService;
import com.fruitude.member.model.MemberCreditAdjustmentRequestDTO;
import com.fruitude.member.model.MemberCreditAdjustmentService;
import com.fruitude.member.model.MemberCreditTransaction;
import com.fruitude.member.model.MemberCreditTransactionDTO;
import com.fruitude.member.model.MemberService;
import com.fruitude.member.model.OnlineMemberTracker;
import com.fruitude.member.model.MemberVO;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 後台會員管理 API，提供分頁清單、搜尋、狀態與購物金管理功能。
 */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

	private final MemberService memberService;
	private final MemberCreditAdjustmentService creditAdjustmentService;
	private final AdminMemberMutationService mutationService;
	private final OnlineMemberTracker onlineMemberTracker;

	public AdminMemberController(MemberService memberService,
			MemberCreditAdjustmentService creditAdjustmentService,
			AdminMemberMutationService mutationService,
			OnlineMemberTracker onlineMemberTracker) {
		this.memberService = memberService;
		this.creditAdjustmentService = creditAdjustmentService;
		this.mutationService = mutationService;
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
	public ResponseEntity<?> getMember(@PathVariable("memberId") Integer memberId) {
		Optional<MemberVO> member = memberService.findById(memberId);
		return member.isPresent()
				? ResponseEntity.ok(member.get())
				: ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
	}

	/** 將會員設為啟用（1）或停權（0）。 */
	@PatchMapping("/{memberId}/status")
	public ResponseEntity<?> updateStatus(@PathVariable("memberId") Integer memberId,
			@RequestBody StatusRequest form, HttpServletRequest request) {
		try {
			Optional<MemberVO> member = mutationService.updateStatus(memberId, form.getMemberStatus(), request);
			if (!member.isPresent()) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("找不到會員"));
			}
			return ResponseEntity.ok(member.get());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 員工送出購物金調整申請，不會立刻改會員餘額。 */
	@PostMapping("/{memberId}/credit-adjustment-requests")
	public ResponseEntity<?> createCreditAdjustmentRequest(@PathVariable("memberId") Integer memberId,
			@RequestBody CreditAdjustmentRequestForm form, HttpServletRequest request) {
		try {
			if (form == null) throw new IllegalArgumentException("購物金調整申請內容不可空白");
			MemberCreditAdjustmentRequestDTO requestDto = mutationService.createCreditAdjustmentRequest(memberId,
					form.getAdjustmentType(), form.getAmount(), form.getReasonCategory(),
					form.getReasonDetail(), request);
			return ResponseEntity.ok(requestDto);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 分頁查詢購物金調整申請。 */
	@GetMapping("/credit-adjustment-requests")
	public ResponseEntity<?> getCreditAdjustmentRequests(
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "status", required = false) Byte status,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		try {
			Page<MemberCreditAdjustmentRequestDTO> requests = creditAdjustmentService.findRequests(
					keyword, status, page, size);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("requests", requests.getContent());
			body.put("page", requests.getNumber());
			body.put("size", requests.getSize());
			body.put("totalPages", requests.getTotalPages());
			body.put("totalElements", requests.getTotalElements());
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 主管核准或退回購物金調整申請。 */
	@PatchMapping("/credit-adjustment-requests/{requestId}/review")
	public ResponseEntity<?> reviewCreditAdjustmentRequest(@PathVariable("requestId") Integer requestId,
			@RequestBody CreditAdjustmentReviewForm form, HttpServletRequest request) {
		try {
			if (form == null || form.getApproved() == null) {
				throw new IllegalArgumentException("請選擇核准或退回");
			}
			boolean approve = form.getApproved().booleanValue();
			MemberCreditAdjustmentRequestDTO requestDto = mutationService.reviewCreditAdjustmentRequest(requestId,
					approve, form.getReviewNote(), request);
			return ResponseEntity.ok(requestDto);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(message(e.getMessage()));
		}
	}

	/** 申請人取消自己的待審核購物金調整單。 */
	@PatchMapping("/credit-adjustment-requests/{requestId}/cancel")
	public ResponseEntity<?> cancelCreditAdjustmentRequest(@PathVariable("requestId") Integer requestId,
			HttpServletRequest request) {
		try {
			MemberCreditAdjustmentRequestDTO requestDto = mutationService.cancelCreditAdjustmentRequest(requestId,
					request);
			return ResponseEntity.ok(requestDto);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));
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
	public ResponseEntity<?> getCreditTransactions(@PathVariable("memberId") Integer memberId,
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

	/** 後台購物金調整申請：增加或扣除指定金額，需另一人核准後才入帳。 */
	public static class CreditAdjustmentRequestForm {
		private Byte adjustmentType;
		private Integer amount;
		private String reasonCategory;
		private String reasonDetail;

		public Byte getAdjustmentType() { return adjustmentType; }
		public void setAdjustmentType(Byte adjustmentType) { this.adjustmentType = adjustmentType; }
		public Integer getAmount() { return amount; }
		public void setAmount(Integer amount) { this.amount = amount; }
		public String getReasonCategory() { return reasonCategory; }
		public void setReasonCategory(String reasonCategory) { this.reasonCategory = reasonCategory; }
		public String getReasonDetail() { return reasonDetail; }
		public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
	}

	/** 主管審核購物金調整申請。approved=true 核准，false 退回。 */
	public static class CreditAdjustmentReviewForm {
		private Boolean approved;
		private String reviewNote;

		public Boolean getApproved() { return approved; }
		public void setApproved(Boolean approved) { this.approved = approved; }
		public String getReviewNote() { return reviewNote; }
		public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
	}
}
