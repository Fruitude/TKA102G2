package com.fruitude.member.model;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.employee.model.OperationAuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 後台會員異動交易服務：確保資料修改與操作紀錄在同一個資料庫交易內完成。
 */
@Service
public class AdminMemberMutationService {

	private final MemberService memberService;
	private final MemberCreditAdjustmentService creditAdjustmentService;
	private final OperationAuditService auditService;

	public AdminMemberMutationService(MemberService memberService,
			MemberCreditAdjustmentService creditAdjustmentService,
			OperationAuditService auditService) {
		this.memberService = memberService;
		this.creditAdjustmentService = creditAdjustmentService;
		this.auditService = auditService;
	}

	/** 修改會員狀態並留下經手員工紀錄；任一步失敗都會整筆回滾。 */
	@Transactional
	public Optional<MemberVO> updateStatus(Integer memberId, Integer memberStatus,
			HttpServletRequest servletRequest) {
		Optional<MemberVO> member = memberService.updateStatus(memberId, memberStatus);
		if (member.isPresent()) {
			MemberVO savedMember = member.get();
			auditService.record(servletRequest, "MEMBER", "STATUS", memberId, savedMember.getMemberName(),
					"會員狀態改為 " + (Integer.valueOf(1).equals(savedMember.getMemberStatus()) ? "正常" : "停權"));
		}
		return member;
	}

	/** 建立待審購物金申請並留下申請紀錄，不會直接更動會員餘額。 */
	@Transactional
	public MemberCreditAdjustmentRequestDTO createCreditAdjustmentRequest(Integer memberId, Byte adjustmentType,
			Integer amount, String reasonCategory, String reasonDetail, HttpServletRequest servletRequest) {
		MemberCreditAdjustmentRequestDTO request = creditAdjustmentService.createRequest(memberId, adjustmentType,
				amount, reasonCategory, reasonDetail, auditService.resolveEmployeeId(servletRequest));
		auditService.record(servletRequest, "MEMBER_CREDIT_REQUEST", "CREATE",
				request.getCreditAdjustmentRequestId(), request.getMemberName(), detail(request));
		return request;
	}

	/** 審核申請並留下核准或退回紀錄；核准入帳與紀錄共用同一個交易。 */
	@Transactional
	public MemberCreditAdjustmentRequestDTO reviewCreditAdjustmentRequest(Integer requestId, boolean approve,
			String reviewNote, HttpServletRequest servletRequest) {
		MemberCreditAdjustmentRequestDTO request = creditAdjustmentService.reviewRequest(requestId, approve,
				reviewNote, auditService.resolveEmployeeId(servletRequest));
		auditService.record(servletRequest, "MEMBER_CREDIT_REQUEST", approve ? "APPROVE" : "REJECT",
				request.getCreditAdjustmentRequestId(), request.getMemberName(), detail(request));
		return request;
	}

	/** 取消自己的待審申請並留下取消紀錄。 */
	@Transactional
	public MemberCreditAdjustmentRequestDTO cancelCreditAdjustmentRequest(Integer requestId,
			HttpServletRequest servletRequest) {
		MemberCreditAdjustmentRequestDTO request = creditAdjustmentService.cancelRequest(requestId,
				auditService.resolveEmployeeId(servletRequest));
		auditService.record(servletRequest, "MEMBER_CREDIT_REQUEST", "CANCEL",
				request.getCreditAdjustmentRequestId(), request.getMemberName(), detail(request));
		return request;
	}

	/** 統一產生購物金申請的操作紀錄內容，避免各個動作留下不同格式。 */
	private String detail(MemberCreditAdjustmentRequestDTO request) {
		String type = Byte.valueOf(MemberCreditAdjustmentRequest.TYPE_ADD).equals(request.getAdjustmentType())
				? "增加" : "扣除";
		return type + "購物金 " + request.getAmount() + " 元；原因："
				+ request.getReasonCategory() + "：" + request.getReasonDetail();
	}
}
