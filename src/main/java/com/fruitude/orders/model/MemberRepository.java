package com.fruitude.orders.model;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fruitude.example.model.ExampleMember;

public interface MemberRepository extends JpaRepository<ExampleMember, Integer>{

}
