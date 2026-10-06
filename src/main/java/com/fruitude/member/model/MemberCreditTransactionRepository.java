package com.fruitude.member.model;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 會員購物金流水資料存取介面。 */
public interface MemberCreditTransactionRepository extends JpaRepository<MemberCreditTransaction, Integer> {

	Page<MemberCreditTransaction> findByMemberId(Integer memberId, Pageable pageable);

	boolean existsByRefundOrderId(Integer refundOrderId);

	@Query("SELECT new com.fruitude.member.model.MemberCreditTransactionDTO("
			+ "t.creditTransactionId, t.memberId, m.memberName, m.memberAccount, "
			+ "t.transactionType, t.amount, t.balanceBefore, t.balanceAfter, "
			+ "t.ordersId, t.refundOrderId, t.employeeId, e.employeeName, "
			+ "t.reason, t.createdAt) "
			+ "FROM MemberCreditTransaction t "
			+ "JOIN MemberVO m ON t.memberId = m.memberId "
			+ "LEFT JOIN Employee e ON t.employeeId = e.employeeId "
			+ "WHERE (:keyword = '' OR LOWER(m.memberName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "    OR LOWER(m.memberAccount) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "    OR LOWER(t.reason) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "    OR (CAST(t.ordersId AS string) LIKE CONCAT('%', :keyword, '%'))) "
			+ "AND (:type IS NULL OR t.transactionType = :type) "
			+ "AND (:startAt IS NULL OR t.createdAt >= :startAt) "
			+ "AND (:endAt IS NULL OR t.createdAt < :endAt)")
	Page<MemberCreditTransactionDTO> searchAllTransactions(
			@Param("keyword") String keyword,
			@Param("type") Byte type,
			@Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt,
			Pageable pageable);
}
