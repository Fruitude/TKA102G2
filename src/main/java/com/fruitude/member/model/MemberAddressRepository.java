package com.fruitude.member.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 會員地址資料存取層，只提供會員編號範圍內的查詢。 */
@Repository
public interface MemberAddressRepository extends JpaRepository<MemberAddress, Integer> {

	List<MemberAddress> findByMemberIdOrderByDefaultValueDescAddressIdAsc(Integer memberId);

	Optional<MemberAddress> findByAddressIdAndMemberId(Integer addressId, Integer memberId);
}
