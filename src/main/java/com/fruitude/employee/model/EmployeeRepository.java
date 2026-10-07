package com.fruitude.employee.model;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 員工帳號資料存取層。 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

	Optional<Employee> findByEmployeeAccountIgnoreCase(String employeeAccount);
	boolean existsByEmployeeAccountIgnoreCase(String employeeAccount);
	boolean existsByEmployeeEmailIgnoreCase(String employeeEmail);
	boolean existsByEmployeePhone(String employeePhone);
	boolean existsByEmployeeAccountIgnoreCaseAndEmployeeIdNot(String employeeAccount, Integer employeeId);
	boolean existsByEmployeeEmailIgnoreCaseAndEmployeeIdNot(String employeeEmail, Integer employeeId);
	boolean existsByEmployeePhoneAndEmployeeIdNot(String employeePhone, Integer employeeId);
	boolean existsByPositionId(Integer positionId);
	List<Employee> findByEmployeeReviewStatusOrderByCreatedAtAsc(Byte employeeReviewStatus);
}
