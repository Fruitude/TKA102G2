package com.fruitude.employee.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 員工帳號資料存取層。 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

	boolean existsByEmployeeAccountIgnoreCase(String employeeAccount);
	boolean existsByEmployeeEmailIgnoreCase(String employeeEmail);
	boolean existsByEmployeeAccountIgnoreCaseAndEmployeeIdNot(String employeeAccount, Integer employeeId);
	boolean existsByEmployeeEmailIgnoreCaseAndEmployeeIdNot(String employeeEmail, Integer employeeId);
	boolean existsByPositionId(Integer positionId);
}
