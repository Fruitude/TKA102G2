package com.fruitude.controller;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.entity.Dept;
import com.fruitude.entity.Employee;
import com.fruitude.service.EmpService;

import jakarta.persistence.Tuple;

@Controller
@RequestMapping("/example/emp")
public class EmpController {
	@Autowired
	private EmpService empService;
	
	@GetMapping({"", "/"})
	public String empMain() {
		return "example/emp_main";
	}

	@GetMapping("/getall")
	public String getAll(Model model) {
		List<Employee> employeeList = empService.findAll();
		model.addAttribute(employeeList);
		return "example/emp";
	}
	
	@GetMapping("/getalljoin")
	public String getAllWithJoin(Model model) {
		List<Tuple> employeeListJoin = empService.findAllWithJoin();
		model.addAttribute("employeeListJoin", employeeListJoin);
		return "example/emp_join";
	}
	
	@GetMapping("/add")
	public String showAddForm(Model model) {
		List<Dept> deptList = empService.getDeptList();
		System.out.println(deptList.get(0).getDname());
		model.addAttribute(deptList);
		return "example/add_emp";
	}

	@PostMapping("/add_emp")
	public String insert(
			@RequestParam("empName") String empName,
			@RequestParam("empJob") String empJob,
			@RequestParam("hireDate")
			@DateTimeFormat(pattern = "yyyy-MM-dd")
			Date hireDate,
			@RequestParam("sal") Integer sal,
			@RequestParam("comm") Double comm,
			@RequestParam("empDeptno") Integer empdeptno,
			RedirectAttributes redirectAttributes) {
		Employee emp = new Employee();
		emp.setEname(empName);
		emp.setJob(empJob);
		emp.setHiredate(hireDate);
		emp.setSal(sal);
		emp.setComm(comm);
		emp.setEmpdeptno(empdeptno);

		boolean success = empService.insert(emp);
		redirectAttributes.addFlashAttribute("message", success ? "新增成功" : "新增失敗");
		return "redirect:/example/emp/add";
	}
	
	@GetMapping("/delete")
	public String showDelForm() {
		return "example/del_emp";
	}
	
	@PostMapping("/delete")
	public String delete(@RequestParam("empno") Integer empno,
			RedirectAttributes redirectAttributes) {
		boolean success = empService.delete(empno);
		redirectAttributes.addFlashAttribute("message", success ? "刪除成功" : "刪除失敗");
		return "redirect:/example/emp/delete";
	}
	
	@GetMapping("/edit")
	public String showEditForm(@RequestParam(value = "empno", required = false) Integer empno, Model model) {
		List<Dept> deptList = empService.getDeptList();
		model.addAttribute(deptList);

		if (empno != null) {
			model.addAttribute("searched", true);
			model.addAttribute("searchedEmpno", empno);
			Optional<Employee> optional = empService.findById(empno);
			model.addAttribute("employee", optional.orElse(null));
		}
		return "example/edit_emp";
	}

	/***
	 *  insert() 跟 update() 整個 method 合併這個我不建議做。
	 *  雖然流程長得像，但語意不同（新增 vs 修改）、redirect 目標不同（/example/emp/add vs /example/emp/edit?empno=X）、
	 *  update 還多一個 empno 必要參數。
	 *  硬要合併成一個「有 empno 就 update，
	 *  沒有就 insert」的 upsert 方法，會多一層 if/else 分支判斷，
	 *  讀起來反而比兩個各自清楚的方法還繞，省下來的重複碼很少，不太划算。
	 */
	@PostMapping("/update")
	public String update(
			@RequestParam("empno") Integer empno,
			@RequestParam("empName") String empName,
			@RequestParam("empJob") String empJob,
			@RequestParam("hireDate")
			@DateTimeFormat(pattern = "yyyy-MM-dd")
			Date hireDate,
			@RequestParam("sal") Integer sal,
			@RequestParam("comm") Double comm,
			@RequestParam("empDeptno") Integer empdeptno,
			RedirectAttributes redirectAttributes) {

		boolean success = empService.updateEmployee(empno, empName, empJob, hireDate, sal, comm, empdeptno);
		redirectAttributes.addFlashAttribute("message", success ? "更新成功" : "更新失敗");
		return "redirect:/example/emp/edit?empno=" + empno;
	}

	// 做法一：先查出既有的 entity，只改 sal，再存回去
	@PostMapping("/updateSalByLoad")
	@ResponseBody
	public String updateSalByLoad(@RequestParam("empno") Integer empno, @RequestParam("sal") Integer sal) {
		boolean success = empService.updateSalByLoad(empno, sal);
		return success ? "更新成功" : "更新失敗";
	}

	// 做法二：直接下 JPQL UPDATE，只改 sal
	@PostMapping("/updateSalByQuery")
	@ResponseBody
	public String updateSalByQuery(@RequestParam("empno") Integer empno, @RequestParam("sal") Integer sal) {
		boolean success = empService.updateSalByQuery(empno, sal);
		return success ? "更新成功" : "更新失敗";
	}
}
