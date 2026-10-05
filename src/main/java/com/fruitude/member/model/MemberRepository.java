package com.fruitude.member.model;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 會員資料存取層。JpaRepository 已提供新增、修改、刪除、單筆及全部查詢等基本功能。
 * 指定獨立的 Bean 名稱，避免和專案原有的同名 MemberRepository 發生衝突。
 */
@Repository("memberModuleRepository")
public interface MemberRepository extends JpaRepository<MemberVO, Integer> {

	// 登入時允許使用帳號或 Email，並明確以不分大小寫的方式尋找會員。
	Optional<MemberVO> findByMemberAccountIgnoreCaseOrMemberEmailIgnoreCase(String memberAccount, String memberEmail);

	// 註冊及修改資料前，用不分大小寫的方式檢查帳號或 Email 是否已被使用。
	boolean existsByMemberAccountIgnoreCase(String memberAccount);

	boolean existsByMemberEmailIgnoreCase(String memberEmail);

	boolean existsByMemberAccountIgnoreCaseAndMemberIdNot(String memberAccount, Integer memberId);

	boolean existsByMemberEmailIgnoreCaseAndMemberIdNot(String memberEmail, Integer memberId);

	/**
	 * 後台會員查詢同時支援關鍵字、狀態及分頁，避免會員數量增加後一次載入全部資料。
	 * keyword 傳空字串代表不限制姓名、帳號與 Email；status 傳 null 代表全部狀態。
	 */
	@Query("SELECT member FROM MemberVO member "
			+ "WHERE (:status IS NULL OR member.memberStatus = :status) "
			+ "AND (:keyword = '' "
			+ "OR LOWER(member.memberName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(member.memberAccount) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(member.memberEmail) LIKE LOWER(CONCAT('%', :keyword, '%')))")
	Page<MemberVO> findAdminPage(@Param("keyword") String keyword, @Param("status") Integer status,
			Pageable pageable);
}
