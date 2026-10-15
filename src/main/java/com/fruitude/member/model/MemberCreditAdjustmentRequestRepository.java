package com.fruitude.member.model;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** 購物金調整申請資料存取介面。 */
public interface MemberCreditAdjustmentRequestRepository
		extends JpaRepository<MemberCreditAdjustmentRequest, Integer> {

	/** 審核或取消前鎖住這筆申請，避免兩人同時處理同一張單。 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT request FROM MemberCreditAdjustmentRequest request "
			+ "WHERE request.creditAdjustmentRequestId = :requestId")
	Optional<MemberCreditAdjustmentRequest> findByIdForUpdate(@Param("requestId") Integer requestId);

	/** 依會員、原因或申請人查詢申請單，並一次帶回畫面所需的顯示名稱。 */
	@Query("SELECT new com.fruitude.member.model.MemberCreditAdjustmentRequestDTO("
			+ "request.creditAdjustmentRequestId, request.memberId, member.memberName, member.memberAccount, "
			+ "request.adjustmentType, request.amount, request.balanceAtRequest, request.reasonCategory, "
			+ "request.reasonDetail, request.requestStatus, request.requestedByEmployeeId, requester.employeeName, "
			+ "request.requestedAt, request.reviewedByEmployeeId, reviewer.employeeName, request.reviewedAt, "
			+ "request.reviewNote, request.creditTransactionId) "
			+ "FROM MemberCreditAdjustmentRequest request "
			+ "JOIN MemberVO member ON request.memberId = member.memberId "
			+ "LEFT JOIN Employee requester ON request.requestedByEmployeeId = requester.employeeId "
			+ "LEFT JOIN Employee reviewer ON request.reviewedByEmployeeId = reviewer.employeeId "
			+ "WHERE (:status IS NULL OR request.requestStatus = :status) "
			+ "AND (:keyword = '' OR LOWER(member.memberName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "    OR LOWER(member.memberAccount) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "    OR LOWER(request.reasonCategory) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "    OR LOWER(request.reasonDetail) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "    OR LOWER(requester.employeeName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
	Page<MemberCreditAdjustmentRequestDTO> searchRequests(
			@Param("keyword") String keyword,
			@Param("status") Byte status,
			Pageable pageable);
}
