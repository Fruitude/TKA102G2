package com.fruitude.promo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.promo.model.PromoGrabService;
import com.fruitude.promo.model.PromoGrabService.GrabResult;

import jakarta.servlet.http.HttpSession;

/** 前台搶購物金 API。會員編號一律取自登入 session，不接受前端傳 */
@RestController
@RequestMapping("/api/promos")
public class PromoGrabApiController {

	@Autowired
	private PromoGrabService promoGrabService;

	/** 搶購面板資料：伺服器時間（前端校正倒數）、進行中與尚未開始的搶購物金活動、會員目前購物金與自己的結果。未登入也可以看 */
	@GetMapping("/grab/state")
	public ResponseEntity<?> state(HttpSession session) {
		Integer memberId = session.getAttribute("loggedInMemberId") instanceof Integer id ? id : null;
		return ResponseEntity.ok()
				.header("Cache-Control", "no-store")
				.body(promoGrabService.state(memberId));
	}

	/** 搶購物金：result 是 WON（搶到）、LOST（名額已滿）、ALREADY_WON / ALREADY_LOST（這場已經搶過） */
	@PostMapping("/{promoProjectId}/grab")
	public ResponseEntity<?> grab(@PathVariable Integer promoProjectId, HttpSession session) {
		if (!(session.getAttribute("loggedInMemberId") instanceof Integer memberId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "請先登入會員"));
		}
		GrabResult result;
		try {
			result = promoGrabService.grab(memberId, promoProjectId);
		} catch (DataIntegrityViolationException e) {
			// 同一位會員的請求同時送出，另一筆先寫入了參加紀錄：重新查一次，回「已經搶過」
			result = promoGrabService.grab(memberId, promoProjectId);
		}
		switch (result.status()) {
		case NOT_STARTED:
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "活動尚未開始"));
		case ENDED:
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "活動已結束"));
		case UNAVAILABLE:
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "找不到這場搶購活動"));
		default:
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("result", result.status().name());
			body.put("slotNo", result.slotNo());
			body.put("credit", result.credit());
			body.put("shoppingCredit", result.shoppingCredit());
			return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
		}
	}
}
