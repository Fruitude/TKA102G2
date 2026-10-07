package com.fruitude.employee.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 後台員工管理頁面的路由，只負責回傳 Thymeleaf 頁面。 */
@Controller
@RequestMapping("/admin/employees")
public class EmployeeAdminPageController {

	@GetMapping({ "", "/" })
	public String employeeManagementPage() {
		return "admin/employees/index";
	}

	/** 顯示後台員工登入頁，不影響既有會員前台登入頁。 */
	@GetMapping("/login")
	public String loginPage() {
		return "admin/employees/login";
	}
}
