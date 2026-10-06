package com.fruitude.orders.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Integer>{

	// 扣會員購物金：餘額夠才扣，檢查與扣款在同一個 UPDATE 完成，兩個訂單同時結帳也不會把餘額扣成負的。
	// 回傳 0 代表餘額不足（或找不到會員）
	@Modifying
	@Query("UPDATE Member m SET m.shoppingCredit = m.shoppingCredit - :amount "
			+ "WHERE m.memberId = :memberId AND m.shoppingCredit >= :amount")
	int deductShoppingCredit(@Param("memberId") Integer memberId, @Param("amount") Integer amount);
}
