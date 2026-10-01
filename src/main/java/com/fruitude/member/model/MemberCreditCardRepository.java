package com.fruitude.member.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 會員信用卡資料存取層；API 不會對外提供 cardToken 欄位。 */
@Repository
public interface MemberCreditCardRepository extends JpaRepository<MemberCreditCard, Integer> {

	List<MemberCreditCard> findByMemberIdOrderByDefaultValueDescCreditCardIdAsc(Integer memberId);

	Optional<MemberCreditCard> findByCreditCardIdAndMemberId(Integer creditCardId, Integer memberId);
}
