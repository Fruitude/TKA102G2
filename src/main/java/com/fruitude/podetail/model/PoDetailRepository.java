package com.fruitude.podetail.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoDetailRepository extends JpaRepository<PoDetailVO, Integer> {
    List<PoDetailVO> findByPoId(Integer poId);   // 查詢條件是 po.id
    
}
