package com.fruitude.member.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 會員資料存取層。JpaRepository 已提供新增、修改、刪除、單筆及全部查詢等基本功能。
 * 指定獨立的 Bean 名稱，避免和專案原有的同名 MemberRepository 發生衝突。
 */
@Repository("memberModuleRepository")
public interface MemberRepository extends JpaRepository<MemberVO, Integer> {

	// 登入時允許使用帳號或 Email 尋找會員。
	Optional<MemberVO> findByMemberAccountOrMemberEmail(String memberAccount, String memberEmail);

	// 註冊及修改資料前，用來檢查帳號或 Email 是否已被使用。
	boolean existsByMemberAccount(String memberAccount);

	boolean existsByMemberEmail(String memberEmail);

	boolean existsByMemberAccountAndMemberIdNot(String memberAccount, Integer memberId);

	boolean existsByMemberEmailAndMemberIdNot(String memberEmail, Integer memberId);

	// 管理員可依會員狀態篩選清單。
	List<MemberVO> findByMemberStatus(Integer memberStatus);

	// 管理員輸入一個關鍵字，即可同時比對姓名、帳號及 Email。
	List<MemberVO> findByMemberNameContainingIgnoreCaseOrMemberAccountContainingIgnoreCaseOrMemberEmailContainingIgnoreCase(
			String memberName, String memberAccount, String memberEmail);
}
