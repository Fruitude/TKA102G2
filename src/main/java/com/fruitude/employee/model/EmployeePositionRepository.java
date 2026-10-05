package com.fruitude.employee.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 員工職位主檔資料存取層。 */
@Repository
public interface EmployeePositionRepository extends JpaRepository<EmployeePosition, Integer> {

	List<EmployeePosition> findAllByOrderByPositionIdAsc();
	boolean existsByPositionCodeIgnoreCase(String positionCode);
	boolean existsByPositionNameIgnoreCase(String positionName);
	boolean existsByPositionNameIgnoreCaseAndPositionIdNot(String positionName, Integer positionId);
}
