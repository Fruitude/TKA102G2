package com.fruitude.member.model;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 後台購物金調整申請：員工送單後不立刻改餘額，需另一位員工核准才入帳。
 */
@Service
@Transactional(readOnly = true)
public class MemberCreditAdjustmentService {

	public static final String REASON_COMPENSATION = "客服補償";
	public static final String REASON_CORRECTION = "帳務更正";
	public static final String REASON_OTHER = "其他";

	private static final Set<String> REASON_CATEGORIES = new LinkedHashSet<>();
	static {
		REASON_CATEGORIES.add(REASON_COMPENSATION);
		REASON_CATEGORIES.add(REASON_CORRECTION);
		REASON_CATEGORIES.add(REASON_OTHER);
	}

	private final MemberRepository memberRepository;
	private final MemberCreditAdjustmentRequestRepository requestRepository;
	private final MemberService memberService;

	public MemberCreditAdjustmentService(MemberRepository memberRepository,
			MemberCreditAdjustmentRequestRepository requestRepository, MemberService memberService) {
		this.memberRepository = memberRepository;
		this.requestRepository = requestRepository;
		this.memberService = memberService;
	}

	/** 員工送出購物金調整申請，只建立待審核單，不改會員餘額。 */
	@Transactional
	public MemberCreditAdjustmentRequestDTO createRequest(Integer memberId, Byte adjustmentType, Integer amount,
			String reasonCategory, String reasonDetail, Integer employeeId) {
		if (employeeId == null) throw new IllegalArgumentException("請先登入後台員工帳號");
		if (memberId == null) throw new IllegalArgumentException("會員編號不可空白");
		validateAdjustmentType(adjustmentType);
		if (amount == null || amount <= 0) throw new IllegalArgumentException("調整金額必須大於 0");
		String category = requireReasonCategory(reasonCategory);
		String detail = requireText(reasonDetail, 255, "請填寫調整原因詳細說明");

		MemberVO member = memberRepository.findById(memberId)
				.orElseThrow(() -> new NoSuchElementException("找不到會員"));
		int balanceAtRequest = member.getShoppingCredit() == null ? 0 : member.getShoppingCredit();
		if (Byte.valueOf(MemberCreditAdjustmentRequest.TYPE_DEDUCT).equals(adjustmentType)
				&& amount > balanceAtRequest) {
			throw new IllegalArgumentException("扣除金額不可大於會員目前購物金");
		}

		MemberCreditAdjustmentRequest request = new MemberCreditAdjustmentRequest();
		request.setMemberId(memberId);
		request.setAdjustmentType(adjustmentType);
		request.setAmount(amount);
		request.setBalanceAtRequest(balanceAtRequest);
		request.setReasonCategory(category);
		request.setReasonDetail(detail);
		request.setRequestStatus(MemberCreditAdjustmentRequest.STATUS_PENDING);
		request.setRequestedByEmployeeId(employeeId);
		return toDto(requestRepository.save(request), member);
	}

	/** 後台分頁查詢購物金調整申請，可搭配關鍵字與狀態。 */
	public Page<MemberCreditAdjustmentRequestDTO> findRequests(String keyword, Byte status, int page, int size) {
		if (page < 0) throw new IllegalArgumentException("頁碼不可小於 0");
		if (size < 1 || size > 100) throw new IllegalArgumentException("每頁筆數必須介於 1 到 100");
		if (status != null && status != MemberCreditAdjustmentRequest.STATUS_PENDING
				&& status != MemberCreditAdjustmentRequest.STATUS_APPROVED
				&& status != MemberCreditAdjustmentRequest.STATUS_REJECTED
				&& status != MemberCreditAdjustmentRequest.STATUS_CANCELLED) {
			throw new IllegalArgumentException("申請狀態不正確");
		}
		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "requestedAt")
						.and(Sort.by(Sort.Direction.DESC, "creditAdjustmentRequestId")));
		String cleanKeyword = keyword == null ? "" : keyword.trim();
		return requestRepository.searchRequests(cleanKeyword, status, pageable);
	}

	/** 主管核准後才入帳；退回時不改購物金。申請人不能審自己的單。 */
	@Transactional
	public MemberCreditAdjustmentRequestDTO reviewRequest(Integer requestId, boolean approve, String reviewNote,
			Integer reviewerEmployeeId) {
		if (reviewerEmployeeId == null) throw new IllegalArgumentException("請先登入後台員工帳號");
		MemberCreditAdjustmentRequest request = requestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new NoSuchElementException("找不到購物金調整申請"));
		if (!Byte.valueOf(MemberCreditAdjustmentRequest.STATUS_PENDING).equals(request.getRequestStatus())) {
			throw new IllegalArgumentException("這筆申請不是待審核狀態，無法審核");
		}
		if (reviewerEmployeeId.equals(request.getRequestedByEmployeeId())) {
			throw new IllegalArgumentException("申請人不能審核自己的購物金調整單");
		}

		String note = reviewNote == null ? "" : reviewNote.trim();
		if (note.length() > 255) throw new IllegalArgumentException("審核意見最多 255 個字");
		if (!approve && note.isEmpty()) throw new IllegalArgumentException("退回申請時請填寫原因");

		request.setReviewedByEmployeeId(reviewerEmployeeId);
		request.setReviewedAt(LocalDateTime.now());
		request.setReviewNote(note.isEmpty() ? null : note);

		if (!approve) {
			request.setRequestStatus(MemberCreditAdjustmentRequest.STATUS_REJECTED);
			return toDto(requestRepository.save(request));
		}

		MemberCreditTransaction transaction = memberService.applyApprovedCreditAdjustment(
				request.getMemberId(), request.getAdjustmentType(), request.getAmount(),
				request.getReasonCategory(), request.getReasonDetail(), reviewerEmployeeId);
		request.setRequestStatus(MemberCreditAdjustmentRequest.STATUS_APPROVED);
		request.setCreditTransactionId(transaction.getCreditTransactionId());
		return toDto(requestRepository.save(request));
	}

	/** 申請人取消自己的待審核單。 */
	@Transactional
	public MemberCreditAdjustmentRequestDTO cancelRequest(Integer requestId, Integer employeeId) {
		if (employeeId == null) throw new IllegalArgumentException("請先登入後台員工帳號");
		MemberCreditAdjustmentRequest request = requestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new NoSuchElementException("找不到購物金調整申請"));
		if (!Byte.valueOf(MemberCreditAdjustmentRequest.STATUS_PENDING).equals(request.getRequestStatus())) {
			throw new IllegalArgumentException("只有待審核的申請可以取消");
		}
		if (!employeeId.equals(request.getRequestedByEmployeeId())) {
			throw new IllegalArgumentException("只能取消自己送出的購物金調整申請");
		}
		request.setRequestStatus(MemberCreditAdjustmentRequest.STATUS_CANCELLED);
		request.setReviewedByEmployeeId(employeeId);
		request.setReviewedAt(LocalDateTime.now());
		request.setReviewNote("申請人取消");
		return toDto(requestRepository.save(request));
	}

	/** 單筆申請異動完成後，重新讀取會員名稱並轉成畫面需要的格式。 */
	private MemberCreditAdjustmentRequestDTO toDto(MemberCreditAdjustmentRequest request) {
		MemberVO member = memberRepository.findById(request.getMemberId()).orElse(null);
		return toDto(request, member);
	}

	/** 將申請實體轉成後台回傳格式；單筆異動時會員名稱由既有會員資料補入。 */
	private MemberCreditAdjustmentRequestDTO toDto(MemberCreditAdjustmentRequest request, MemberVO member) {
		return new MemberCreditAdjustmentRequestDTO(
				request.getCreditAdjustmentRequestId(),
				request.getMemberId(),
				member == null ? null : member.getMemberName(),
				member == null ? null : member.getMemberAccount(),
				request.getAdjustmentType(),
				request.getAmount(),
				request.getBalanceAtRequest(),
				request.getReasonCategory(),
				request.getReasonDetail(),
				request.getRequestStatus(),
				request.getRequestedByEmployeeId(),
				null,
				request.getRequestedAt(),
				request.getReviewedByEmployeeId(),
				null,
				request.getReviewedAt(),
				request.getReviewNote(),
				request.getCreditTransactionId());
	}

	/** 限制調整類型只能使用系統定義的增加或扣除代碼。 */
	private void validateAdjustmentType(Byte adjustmentType) {
		if (!Byte.valueOf(MemberCreditAdjustmentRequest.TYPE_ADD).equals(adjustmentType)
				&& !Byte.valueOf(MemberCreditAdjustmentRequest.TYPE_DEDUCT).equals(adjustmentType)) {
			throw new IllegalArgumentException("調整類型只能是增加或扣除");
		}
	}

	/** 檢查原因分類必須來自後台允許的固定選項。 */
	private String requireReasonCategory(String reasonCategory) {
		String clean = reasonCategory == null ? "" : reasonCategory.trim();
		if (clean.isEmpty()) throw new IllegalArgumentException("請選擇調整原因分類");
		if (!REASON_CATEGORIES.contains(clean)) throw new IllegalArgumentException("調整原因分類不正確");
		return clean;
	}

	/** 清除文字前後空白，並統一檢查必填與長度上限。 */
	private String requireText(String value, int maxLength, String emptyMessage) {
		String clean = value == null ? "" : value.trim();
		if (clean.isEmpty()) throw new IllegalArgumentException(emptyMessage);
		if (clean.length() > maxLength) throw new IllegalArgumentException("內容最多 " + maxLength + " 個字");
		return clean;
	}
}
