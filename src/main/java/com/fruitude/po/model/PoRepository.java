package com.fruitude.po.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.fruitude.employee.model.Employee;


public interface PoRepository  extends JpaRepository<PoVO, Integer>  {
	
	boolean existsByPoNo(String poNo);
	
	List<PoVO> getByPoStatus(Byte poStatus);
	
	List<PoVO> findByPoEmployeeId(Employee employee);

	// 取採購單編號開頭相同（同一天）且編號最大的一筆，用來產生下一個流水號
	Optional<PoVO> findTopByPoNoStartingWithOrderByPoNoDesc(String poNoPrefix);
	
}
