package com.fruitude.employee.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 員工與權限功能關聯的資料存取層。 */
@Repository
public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Integer> {

	List<EmployeePermission> findByEmployeeIdOrderByPermissionIdAsc(Integer employeeId);
	long countByEmployeeId(Integer employeeId);

	/** 直接取得員工已分配的權限代碼，供後台網址與 API 驗證使用。 */
	@Query(value = "SELECT f.permission_code FROM employee_permission ep "
			+ "JOIN employee_permission_function f ON f.permission_id = ep.permission_id "
			+ "WHERE ep.employee_id = :employeeId", nativeQuery = true)
	List<String> findPermissionCodesByEmployeeId(@Param("employeeId") Integer employeeId);
}
