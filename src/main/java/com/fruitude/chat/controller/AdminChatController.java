package com.fruitude.chat.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import com.fruitude.chat.model.CannedReplyService;
import com.fruitude.chat.model.ChatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

// 後台客服聊天室：頁面與頁面用的 API
// 員工登入由 AdminAccessInterceptor 擋（/admin/**、/api/admin/**）；聊天室的權限在這裡自己檢查：
// 查看要 CHAT_VIEW、送訊息要 CHAT_REPLY，權限取自 AdminAccessInterceptor 放進 request 的 adminPermissionCodes
@Controller
public class AdminChatController {

	@Autowired
	private ChatService chatSvc;

	@Autowired
	private CannedReplyService cannedReplySvc;

	// 聊天室頁面：沒有 CHAT_VIEW 時導到沒有權限的頁面；canReply 決定頁面要不要顯示輸入框
	@GetMapping("/admin/chat")
	public String chat(HttpServletRequest request, Model model) {
		if (!hasPermission(request, "CHAT_VIEW")) {
			return "redirect:/admin/access-denied";
		}
		model.addAttribute("canReply", hasPermission(request, "CHAT_REPLY"));
		return "admin/chat/index"; //view
	}

    // Shared sidebar badge: expose only the total and require the same CHAT_VIEW permission.
    @GetMapping("/api/admin/chat/unread")
    @ResponseBody
    public ResponseEntity<?> unread(HttpServletRequest request) {
        if(!hasPermission(request,"CHAT_VIEW"))return forbidden();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("unreadCount",chatSvc.staffUnreadCount()));
    }

	// 對話清單：每位會員一列，最後訊息時間由新到舊
	@GetMapping("/api/admin/chat/conversations")
	@ResponseBody
	public ResponseEntity<?> conversations(HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_VIEW")) {
			return forbidden();
		}
		return ResponseEntity.ok(chatSvc.getConversationList());
	}

	// 一位會員的對話內容；客服開啟對話就算讀過，順便把會員送來的未讀訊息改成已讀
	@GetMapping("/api/admin/chat/{memberId}/messages")
	@ResponseBody
	public ResponseEntity<?> messages(@PathVariable Integer memberId, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_VIEW")) {
			return forbidden();
		}
		chatSvc.markReadByStaff(memberId);
		return ResponseEntity.ok(chatSvc.getStaffConversation(memberId));
	}

	// 對話列預覽：這位會員全部的未讀訊息；只看不標已讀，給對話列的眼睛按鈕和右鍵選單用
	@GetMapping("/api/admin/chat/{memberId}/preview")
	@ResponseBody
	public ResponseEntity<?> preview(@PathVariable Integer memberId, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_VIEW")) {
			return forbidden();
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chatSvc.getUnreadPreview(memberId));
	}

	// 建議回覆：拿這位會員最新一則訊息比對罐頭訊息的關鍵字，最多 3 筆；只有能回覆的員工用得到
	@GetMapping("/api/admin/chat/{memberId}/suggestions")
	@ResponseBody
	public ResponseEntity<?> suggestions(@PathVariable Integer memberId, HttpServletRequest request) {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		return ResponseEntity.ok(cannedReplySvc.getSuggestions(memberId));
	}

	// 客服送出訊息：multipart/form-data，content 是文字、images 是 0 ~ 5 張圖片
	// cannedId 是由建議回覆帶入時的罐頭訊息編號（沒有就不帶），有的話存成罐頭訊息
	// 員工編號一律取自登入 session，不接受前端傳
	@PostMapping("/api/admin/chat/{memberId}/messages")
	@ResponseBody
	public ResponseEntity<?> send(@PathVariable Integer memberId,
			@RequestParam(value = "content", required = false) String content,
			@RequestParam(value = "images", required = false) List<MultipartFile> images,
			@RequestParam(value = "cannedId", required = false) Integer cannedId, @RequestParam(value="replyMessageId",required=false) Integer replyMessageId, HttpServletRequest request,
			HttpSession session) throws IOException {
		if (!hasPermission(request, "CHAT_REPLY")) {
			return forbidden();
		}
		Object employeeId = session.getAttribute("loggedInEmployeeId");
		return ResponseEntity.ok(chatSvc.sendStaffMessage(memberId,
				employeeId instanceof Number ? ((Number) employeeId).intValue() : null, content, images, cannedId,replyMessageId));
	}

	// 聊天圖片原圖；頁面點縮圖時在新分頁開這個網址
	@GetMapping("/api/admin/chat/images/{imageId}")
	public ResponseEntity<?> image(@PathVariable Integer imageId, HttpServletRequest request, WebRequest webRequest)
			throws IOException {
		return imageResponse(imageId, false, request, webRequest);
	}

	// 聊天圖片縮圖（最長邊 100px 的 JPEG）；對話中顯示這個
	@GetMapping("/api/admin/chat/images/{imageId}/thumbnail")
	public ResponseEntity<?> thumbnail(@PathVariable Integer imageId, HttpServletRequest request,
			WebRequest webRequest) throws IOException {
		return imageResponse(imageId, true, request, webRequest);
	}

	// 圖片回應，照商品圖片（ProductImageController 的 imageResponse）的規則：
	// 查不到回 404 且不快取；用 ETag 比對，瀏覽器已經有同一張圖時回 304；只快取在使用者自己的瀏覽器
	// 加 nosniff 不讓瀏覽器自己猜類型，Content-Disposition 用 inline 直接顯示
	// 聊天圖片送出後不會再改，所以不用商品的版本號，固定快取 30 分鐘
	private ResponseEntity<?> imageResponse(Integer imageId, boolean thumbnail, HttpServletRequest request,
			WebRequest webRequest) throws IOException {
		if (!hasPermission(request, "CHAT_VIEW")) {
			return forbidden();
		}
		ChatService.ChatImage image = chatSvc.getImage(imageId, thumbnail);
		if (image == null) {
			return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
		}
		CacheControl cache = CacheControl.maxAge(Duration.ofMinutes(30)).cachePrivate();
		if (webRequest.checkNotModified(image.etag())) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(image.etag()).cacheControl(cache).build();
		}
		return ResponseEntity.ok().eTag(image.etag()).cacheControl(cache)
				.header("X-Content-Type-Options", "nosniff")
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("image-" + imageId).build().toString())
				.contentType(MediaType.parseMediaType(image.type())).body(image.data());
	}

	// 內容空白、太長、圖片不符合規則、查無會員等，回 400 並帶原因
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseBody
	public ResponseEntity<?> badRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
	}

	// 上傳的檔案讀取失敗
	@ExceptionHandler(IOException.class)
	@ResponseBody
	public ResponseEntity<?> ioError(IOException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "圖片讀取失敗"));
	}

	// 目前員工有沒有這個權限；adminPermissionCodes 由 AdminAccessInterceptor 放入，沒有時當作沒有權限
	private boolean hasPermission(HttpServletRequest request, String permissionCode) {
		return request.getAttribute("adminPermissionCodes") instanceof Set<?> permissionCodes
				&& permissionCodes.contains(permissionCode);
	}

	private ResponseEntity<?> forbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "你的職位沒有此功能權限"));
	}

}
