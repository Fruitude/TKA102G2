package com.fruitude.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fruitude.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Integer>{

}
