package com.fruitude.employee.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 員工與權限功能關聯的資料存取層。 */
@Repository
public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Integer> {

	List<EmployeePermission> findByEmployeeIdOrderByPermissionIdAsc(Integer employeeId);
	long countByEmployeeId(Integer employeeId);
}
