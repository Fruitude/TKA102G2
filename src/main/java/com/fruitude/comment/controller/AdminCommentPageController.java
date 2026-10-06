package com.fruitude.comment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 後台評論管理頁面路由，只負責回傳評論管理 Thymeleaf 頁面。 */
@Controller
@RequestMapping("/admin/comments")
public class AdminCommentPageController {

	@GetMapping({ "", "/" })
	public String commentManagementPage(Model model) {
		model.addAttribute("activeMenu", "comments");
		return "admin/comments/index";
	}
}
