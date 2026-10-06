package com.fruitude.comment.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.comment.model.CommentAdminRow;
import com.fruitude.comment.model.CommentAdminService;
import com.fruitude.employee.model.OperationAuditService;

import jakarta.servlet.http.HttpServletRequest;

/** 後台評論管理 API：提供評論查詢、檢舉佇列及檢舉判定。 */
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

	private final CommentAdminService commentAdminService;
	private final OperationAuditService auditService;

	public AdminCommentController(CommentAdminService commentAdminService, OperationAuditService auditService) {
		this.commentAdminService = commentAdminService;
		this.auditService = auditService;
	}

	/** 依搜尋條件回傳評論清單；沒有後台登入 Session 時不提供會員評論資料。 */
	@GetMapping
	public ResponseEntity<?> findComments(
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "status", required = false) Integer status,
			@RequestParam(name = "rating", required = false) Integer rating,
			@RequestParam(name = "startDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(name = "endDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			HttpServletRequest request) {
		if (auditService.resolveEmployeeId(request) == null) return unauthorized();
		return ResponseEntity.ok(commentAdminService.search(keyword, status, rating, startDate, endDate, page, size));
	}

	/** 管理員判定被檢舉評論要保留或隱藏，判定說明另外保存到操作紀錄。 */
	@Transactional
	@PutMapping("/{ordersDetailId}/moderation")
	public ResponseEntity<?> moderate(@PathVariable("ordersDetailId") Integer ordersDetailId,
			@RequestBody ModerationForm form, HttpServletRequest request) {
		if (auditService.resolveEmployeeId(request) == null) return unauthorized();
		String note = form.getNote() == null ? "" : form.getNote().trim();
		if (note.length() < 5) throw new IllegalArgumentException("判定說明至少需要 5 個字");
		if (note.length() > 255) throw new IllegalArgumentException("判定說明最多 255 個字");

		CommentAdminRow comment = commentAdminService.moderate(ordersDetailId, form.getAction());
		boolean hidden = comment.getCommentStatus() == CommentAdminService.STATUS_HIDDEN;
		auditService.record(request, "COMMENT", hidden ? "HIDE" : "KEEP", ordersDetailId,
				comment.getProductName(), (hidden ? "檢舉成立，隱藏評論：" : "檢舉不成立，保留評論：") + note);
		return ResponseEntity.ok(comment);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException error) {
		return ResponseEntity.badRequest().body(message(error.getMessage()));
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException error) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(error.getMessage()));
	}

	private ResponseEntity<Map<String, String>> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("請先登入後台員工帳號"));
	}

	private Map<String, String> message(String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	/** 檢舉判定表單：action 只能是 KEEP 或 HIDE，note 保存至操作紀錄。 */
	public static class ModerationForm {
		private String action;
		private String note;

		public String getAction() { return action; }
		public void setAction(String action) { this.action = action; }
		public String getNote() { return note; }
		public void setNote(String note) { this.note = note; }
	}
}
