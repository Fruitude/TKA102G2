package com.fruitude.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fruitude.entity.Employee;

import jakarta.persistence.Tuple;

@Repository
public interface EmpRepository extends JpaRepository<Employee, Integer> {
	//注意，不用寫 SessionFactory/Session 下 HQL，Spring 已經包裝好，
	//使用 @Query 即可下指令
	@Query("SELECT E AS emp, D AS dept FROM Employee E JOIN Dept D ON E.empdeptno = D.deptno order by E.empno")
	List<Tuple> findAllWithJoin();

	// 做法二：直接下 JPQL UPDATE，只改 sal 這個欄位，不會動到其他欄位
	// clearAutomatically = true：更新後清掉 persistence context 快取，避免後續讀到更新前的舊值
	@Modifying(clearAutomatically = true)
	@Query("UPDATE Employee e SET e.sal = :sal WHERE e.empno = :empno")
	int updateSal(@Param("empno") Integer empno, @Param("sal") Integer sal);
}
