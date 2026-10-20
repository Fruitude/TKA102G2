package com.fruitude.quiz.controller;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import com.fruitude.quiz.model.ChatQuizService;

/** Existing member quiz URLs, owned by the quiz module. */
@RestController
@RequestMapping("/api/front/chat/quizzes")
public class QuizController {
    private final ChatQuizService quizzes;
    public QuizController(ChatQuizService quizzes){this.quizzes=quizzes;}
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
    @SuppressWarnings("unchecked")
    private java.util.LinkedHashMap<String,com.fruitude.quiz.model.ChatQuizService.Run> quizRuns(jakarta.servlet.http.HttpSession session){
        Object stored=session.getAttribute("chat.quiz.runs");
        if(stored instanceof java.util.LinkedHashMap<?,?>)return (java.util.LinkedHashMap<String,com.fruitude.quiz.model.ChatQuizService.Run>)stored;
        var runs=new java.util.LinkedHashMap<String,com.fruitude.quiz.model.ChatQuizService.Run>();session.setAttribute("chat.quiz.runs",runs);return runs;
    }
    @GetMapping
    public ResponseEntity<?> quizzes(HttpServletRequest request){member(request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.themes());}
    @GetMapping("/results")
    public ResponseEntity<?> quizHistory(@RequestParam(required=false) Integer beforeId,HttpServletRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.history(member(request),beforeId));
    }
    @PostMapping("/{quizId}/start")
    public ResponseEntity<?> startQuiz(@PathVariable Integer quizId,HttpServletRequest request){
        int id=member(request);mutation(request);var session=request.getSession(false);
        synchronized(session){var run=quizzes.start(id,quizId);var runs=quizRuns(session);while(runs.size()>=5)runs.remove(runs.keySet().iterator().next());runs.put(run.token,run);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(run.view());}
    }
    @PostMapping("/{quizId}/submit")
    public ResponseEntity<?> finishQuiz(@PathVariable Integer quizId,@RequestBody com.fruitude.quiz.model.ChatQuizService.Submission submission,HttpServletRequest request){
        int id=member(request);mutation(request);var session=request.getSession(false);
        synchronized(session){var run=submission==null?null:quizRuns(session).get(submission.token());
            if(run==null||!run.belongsTo(id,quizId))throw new IllegalArgumentException("測驗已過期，請重新開始。");
            if(run.completed==null)run.completed=quizzes.finish(id,quizId,run,submission.answers());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(run.completed);}
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException error){
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("message",error.getMessage()));
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> denied(ResponseStatusException error){
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("message",error.getReason()));
    }
}
