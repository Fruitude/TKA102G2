package com.fruitude.po.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.fruitude.employee.model.Employee;


public interface PoRepository  extends JpaRepository<PoVO, Integer>  {
	
	boolean existsByPoNo(String poNo);
	
	List<PoVO> getByInboundStatus(Byte inboundStatus);
	
	List<PoVO> findByPoEmployeeId(Employee employee);
	
}
