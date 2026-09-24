package com.fruitude.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.entity.Dept;
import com.fruitude.entity.Employee;
import com.fruitude.repository.DeptRepository;
import com.fruitude.repository.EmpRepository;

import jakarta.persistence.Tuple;

@Service
public class EmpService {
	@Autowired
	private EmpRepository empRepository;
	@Autowired
	private DeptRepository deptRepository;

	public List<Employee> findAll() {
		return empRepository.findAll();
	}
	
	public List<Tuple> findAllWithJoin() {
		return empRepository.findAllWithJoin();
	}

	public Optional<Employee> findById(Integer id) {
		return empRepository.findById(id);
	}

	public List<Dept> getDeptList() {
		return deptRepository.findAll();
	}

	public boolean insert(Employee entity) {
//		if (empRepository.existsById(entity.getEmpno())) {
//			return false;
//		}
		try {
			empRepository.saveAndFlush(entity);
			return true;
		} catch (DataAccessException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean delete(Integer id) {
		try {
			empRepository.deleteById(id);
			return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
	
	// 做法一：先查出既有的 entity，只改要改的欄位，再存回去（其他欄位維持原值）
	@Transactional
	public boolean updateSalByLoad(Integer empno, Integer sal) {
		Optional<Employee> optional = empRepository.findById(empno);
		if (optional.isEmpty()) {
			return false;
		}
		Employee emp = optional.get();
		emp.setSal(sal);
		try {
			empRepository.saveAndFlush(emp);
			return true;
		} catch (DataAccessException e) {
			e.printStackTrace();
			return false;
		}
	}

	// 做法二：不查整筆 entity，直接下 JPQL UPDATE 只改 sal 這個欄位
	@Transactional
	public boolean updateSalByQuery(Integer empno, Integer sal) {
		try {
			int updatedRows = empRepository.updateSal(empno, sal);
			return updatedRows > 0;
		} catch (DataAccessException e) {
			e.printStackTrace();
			return false;
		}
	}

	// 編輯頁用：先查出既有 entity，把表單送來的欄位整批蓋上去，再存回去
	@Transactional
	public boolean updateEmployee(Integer empno, String ename, String job, Date hiredate,
			Integer sal, Double comm, Integer empdeptno) {
		Optional<Employee> optional = empRepository.findById(empno);
		if (optional.isEmpty()) {
			return false;
		}
		Employee emp = optional.get();
		emp.setEname(ename);
		emp.setJob(job);
		emp.setHiredate(hiredate);
		emp.setSal(sal);
		emp.setComm(comm);
		emp.setEmpdeptno(empdeptno);
		try {
			empRepository.saveAndFlush(emp);
			return true;
		} catch (DataAccessException e) {
			e.printStackTrace();
			return false;
		}
	}
}
