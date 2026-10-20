package com.fruitude.chat.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fruitude.chat.model.ChatService;
import jakarta.servlet.http.HttpServletRequest;

/** All member and image ownership is resolved server-side; clients cannot choose a conversation. */
@RestController
@RequestMapping("/api/front/chat")
public class FrontChatController {
    private final ChatService chats;
    public FrontChatController(ChatService chats){this.chats=chats;}
    private int member(HttpServletRequest request){
        var session=request.getSession(false);
        Object id=session==null?null:session.getAttribute("loggedInMemberId");
        if(!(id instanceof Number))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"請先登入會員");
        return ((Number)id).intValue();
    }
    private void mutation(HttpServletRequest request){
        // A cross-origin HTML form cannot add this header; no CORS access is granted to this API.
        if(!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
            || "cross-site".equals(request.getHeader("Sec-Fetch-Site")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"請從商城聊天室操作");
    }
    @GetMapping("/messages")
    public ResponseEntity<?> messages(@RequestParam(required=false) Integer beforeId,
        @RequestParam(required=false) Integer afterId,HttpServletRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chats.getMemberConversation(member(request),beforeId,afterId));
    }
    @GetMapping("/unread") public ResponseEntity<?> unread(HttpServletRequest request){
        int id=member(request);
        var session=request.getSession(false);String welcomeToken;
        synchronized(session){
            Object stored=session.getAttribute("chat.welcome.token");
            if(!(stored instanceof String)){stored=java.util.UUID.randomUUID().toString();session.setAttribute("chat.welcome.token",stored);}
            welcomeToken=(String)stored;
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("memberId",id,"unreadCount",chats.memberUnread(id),"welcomeToken",welcomeToken));
    }
    @PostMapping("/messages")
    public ResponseEntity<?> send(@RequestParam(required=false) String content,
        @RequestParam(required=false) List<MultipartFile> images,@RequestParam(required=false) Integer replyMessageId,@RequestParam(required=false) Integer productSkuId,HttpServletRequest request)throws IOException {
        int id=member(request);mutation(request);
        // Quiz commands are UI actions, not customer-service messages: no source, unread count, cache or push.
        if(com.fruitude.quiz.model.ChatQuizService.isQuizRequest(content)){
            if(content.strip().length()>1000)throw new IllegalArgumentException("訊息內容最多 1000 字");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("action","FRUIT_QUIZ"));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chats.sendMemberMessage(id,content,images,replyMessageId,productSkuId));
    }
    @PostMapping("/read") public ResponseEntity<?> read(@RequestParam Integer throughId,HttpServletRequest request){
        int id=member(request);mutation(request);chats.markReadByMember(id,throughId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("unreadCount",chats.memberUnread(id)));
    }
    @GetMapping({"/images/{imageId}","/images/{imageId}/thumbnail"})
    public ResponseEntity<?> image(@PathVariable Integer imageId,HttpServletRequest request)throws IOException {
        int id=member(request);
        var image=chats.getMemberImage(id,imageId,request.getRequestURI().endsWith("/thumbnail"));
        if(image==null)return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        // Revalidate ownership on every request, including after account switching in the same browser.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"chat-image\"")
            .contentType(MediaType.parseMediaType(image.type())).body(image.data());
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException error){
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("message",error.getMessage()));
    }
    @ExceptionHandler(IOException.class) public ResponseEntity<?> invalidImage(IOException error){
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("message","圖片讀取失敗，請重新選取"));
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> denied(ResponseStatusException error){
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("message",error.getReason()));
    }
}
