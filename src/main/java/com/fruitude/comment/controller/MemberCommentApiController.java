package com.fruitude.comment.controller;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.comment.model.MemberCommentService;

import jakarta.servlet.http.HttpSession;

/** 登入會員的商品評論 API。會員編號一律取自登入 session，不接受前端傳 */
@RestController
@RequestMapping("/api/members/me/orders/{ordersId}/reviews")
public class MemberCommentApiController {

	@Autowired
	private MemberCommentService commentService;

	public record ReviewRequest(Integer ordersDetailId, Integer star, String text) {
	}

	/** 這張訂單的各商品與評論狀態 */
	@GetMapping
	public ResponseEntity<?> items(@PathVariable Integer ordersId, HttpSession session) {
		if (!(session.getAttribute("loggedInMemberId") instanceof Integer memberId)) {
			return unauthorized();
		}
		return ResponseEntity.ok(commentService.findOrderItems(memberId, ordersId));
	}

	/** 對訂單內的一個商品送出評論（星數 1~5、內容 1~800 字） */
	@PostMapping
	public ResponseEntity<?> submit(@PathVariable Integer ordersId, @RequestBody ReviewRequest request,
			HttpSession session) {
		if (!(session.getAttribute("loggedInMemberId") instanceof Integer memberId)) {
			return unauthorized();
		}
		commentService.submit(memberId, ordersId, request.ordersDetailId(), request.star(), request.text());
		return ResponseEntity.ok(Map.of("message", "評論已送出"));
	}

	private ResponseEntity<?> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "請先登入會員"));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<?> badRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<?> notFound(NoSuchElementException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(MemberCommentService.DuplicateReviewException.class)
	public ResponseEntity<?> conflict(MemberCommentService.DuplicateReviewException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
	}
}
