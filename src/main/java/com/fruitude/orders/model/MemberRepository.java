package com.fruitude.orders.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fruitude.member.model.MemberVO;

public interface MemberRepository extends JpaRepository<MemberVO, Integer>{

	// 扣會員購物金：餘額夠才扣，檢查與扣款在同一個 UPDATE 完成，兩個訂單同時結帳也不會把餘額扣成負的。
	// 回傳 0 代表餘額不足（或找不到會員）
	@Modifying
	@Query("UPDATE MemberVO m SET m.shoppingCredit = m.shoppingCredit - :amount "
			+ "WHERE m.memberId = :memberId AND m.shoppingCredit >= :amount")
	int deductShoppingCredit(@Param("memberId") Integer memberId, @Param("amount") Integer amount);

	// 加會員購物金（搶購物金發放用）：在資料庫端相加，同時多筆入帳不會互相覆蓋。回傳 0 代表找不到會員
	@Modifying
	@Query("UPDATE MemberVO m SET m.shoppingCredit = m.shoppingCredit + :amount WHERE m.memberId = :memberId")
	int addShoppingCredit(@Param("memberId") Integer memberId, @Param("amount") Integer amount);

	// 目前購物金餘額（純量查詢，不吃 persistence context 裡舊的 entity）
	@Query("SELECT m.shoppingCredit FROM MemberVO m WHERE m.memberId = :memberId")
	Integer findShoppingCredit(@Param("memberId") Integer memberId);
}
