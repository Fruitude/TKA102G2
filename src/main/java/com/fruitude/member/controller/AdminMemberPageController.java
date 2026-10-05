package com.fruitude.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 後台會員管理頁面的路由，只負責回傳會員管理 Thymeleaf 頁面。 */
@Controller
@RequestMapping("/admin/members")
public class AdminMemberPageController {

	@GetMapping({ "", "/" })
	public String memberManagementPage() {
		return "admin/members/index";
	}
}
