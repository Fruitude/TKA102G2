package com.fruitude.employee.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 權限功能主檔資料存取層。 */
@Repository
public interface EmployeePermissionFunctionRepository extends JpaRepository<EmployeePermissionFunction, Integer> {

	List<EmployeePermissionFunction> findAllByOrderByPermissionGroupAscPermissionIdAsc();
}
