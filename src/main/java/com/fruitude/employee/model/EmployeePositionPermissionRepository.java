package com.fruitude.employee.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 職位預設權限的資料存取層。 */
@Repository
public interface EmployeePositionPermissionRepository extends JpaRepository<EmployeePositionPermission, Integer> {

	List<EmployeePositionPermission> findByPositionIdOrderByPermissionIdAsc(Integer positionId);
	long countByPositionId(Integer positionId);
}
