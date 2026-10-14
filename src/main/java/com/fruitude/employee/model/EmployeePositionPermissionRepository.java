package com.fruitude.employee.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 職位預設權限的資料存取層。 */
@Repository
public interface EmployeePositionPermissionRepository extends JpaRepository<EmployeePositionPermission, Integer> {

	List<EmployeePositionPermission> findByPositionIdOrderByPermissionIdAsc(Integer positionId);
	long countByPositionId(Integer positionId);

	/** 直接取得職位預設的權限代碼，供後台登入門禁合併員工額外權限。 */
	@Query(value = "SELECT f.permission_code FROM employee_position_permission epp "
			+ "JOIN employee_permission_function f ON f.permission_id = epp.permission_id "
			+ "WHERE epp.position_id = :positionId", nativeQuery = true)
	List<String> findPermissionCodesByPositionId(@Param("positionId") Integer positionId);
}
