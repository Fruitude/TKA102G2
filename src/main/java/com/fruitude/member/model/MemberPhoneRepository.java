package com.fruitude.member.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 會員電話資料存取層，只提供會員編號範圍內的查詢。 */
@Repository
public interface MemberPhoneRepository extends JpaRepository<MemberPhone, Integer> {

	List<MemberPhone> findByMemberIdOrderByDefaultValueDescPhoneIdAsc(Integer memberId);

	Optional<MemberPhone> findByPhoneIdAndMemberId(Integer phoneId, Integer memberId);
}
