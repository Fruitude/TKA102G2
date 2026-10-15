package com.fruitude.comment.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.comment.model.MemberCommentService;

import jakarta.servlet.http.HttpSession;

/** 登入會員的「我的評論」清單 API。會員編號一律取自登入 session，不接受前端傳 */
@RestController
@RequestMapping("/api/members/me/reviews")
public class MemberReviewListApiController {

	@Autowired
	private MemberCommentService commentService;

	/** 目前登入會員寫過的評論（新的在前），分頁：page 從 0 開始，size 預設 20 */
	@GetMapping
	public ResponseEntity<?> myReviews(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, HttpSession session) {
		if (!(session.getAttribute("loggedInMemberId") instanceof Integer memberId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "請先登入會員"));
		}
		return ResponseEntity.ok(commentService.findMyReviews(memberId, page, size));
	}
}
