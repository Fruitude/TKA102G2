package com.fruitude.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fruitude.entity.Dept;

@Repository
public interface DeptRepository extends JpaRepository<Dept, Integer>{

}
