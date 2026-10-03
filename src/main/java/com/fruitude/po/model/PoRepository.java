package com.fruitude.po.model;

import org.springframework.data.jpa.repository.JpaRepository;


public interface PoRepository  extends JpaRepository<PoVO, Integer>  {
	
	boolean existsByPoNo(String poNo);
	
}
