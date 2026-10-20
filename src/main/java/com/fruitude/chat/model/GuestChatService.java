package com.fruitude.chat.model;

import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import com.fruitude.quiz.model.ChatQuizService;
import tools.jackson.databind.json.JsonMapper;

/** Visitor history is Redis-only. Never calls ChatService or publishes staff events. */
@Service
public class GuestChatService {
    private static final Duration TTL=Duration.ofHours(24);
    private static final String PREFIX="chat:guest:v1:";
    private static final DefaultRedisScript<Long> RATE=new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> APPEND=new DefaultRedisScript<>(
        "local out={}; for i=1,#ARGV do local n=redis.call('INCR',KEYS[2]); "
        +"local s=string.gsub(ARGV[i],'\"messageId\":0','\"messageId\":'..n,1); redis.call('RPUSH',KEYS[1],s); out[i]=s end; "
        +"redis.call('LTRIM',KEYS[1],-100,-1); redis.call('EXPIRE',KEYS[1],86400); redis.call('EXPIRE',KEYS[2],86400); return out",List.class);
    private static final DefaultRedisScript<Long> COMPLETE=new DefaultRedisScript<>(
        "if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end; "
        +"local n=redis.call('INCR',KEYS[3]); local s=string.gsub(ARGV[3],'\"answerId\":0','\"answerId\":'..n,1); "
        +"redis.call('SET',KEYS[1],ARGV[2],'KEEPTTL'); redis.call('RPUSH',KEYS[2],s); "
        +"redis.call('LTRIM',KEYS[2],-20,-1); redis.call('EXPIRE',KEYS[2],86400); redis.call('EXPIRE',KEYS[3],86400); return 1",Long.class);
    private final StringRedisTemplate redis;
    private final CannedReplyService replies;
    private final ChatQuizService quizzes;
    private final JsonMapper json=JsonMapper.builder().build();
    public GuestChatService(StringRedisTemplate redis,CannedReplyService replies,ChatQuizService quizzes){
        this.redis=redis;this.replies=replies;this.quizzes=quizzes;
    }
    public record StoredRun(ChatQuizService.RunSnapshot snapshot,ChatQuizService.Result completed) {}
    public String visitor(HttpServletRequest request){
        var session=request.getSession();
        synchronized(session){
            Object id=session.getAttribute("chat.guest.id");
            if(!(id instanceof String)||redis.opsForValue().get(key((String)id,"active"))==null){id=UUID.randomUUID().toString();session.setAttribute("chat.guest.id",id);}
            redis.opsForValue().set(key((String)id,"active"),"1",TTL);
            for(String suffix:List.of("messages","sequence","quiz-results","quiz-sequence"))redis.expire(key((String)id,suffix),TTL);
            return (String)id;
        }
    }
    private String key(String id,String suffix){return PREFIX+id+":"+suffix;}
    public void limit(String id,HttpServletRequest request){
        // Per-session and per-address limits: making new sessions does not bypass the address budget.
        String address=UUID.nameUUIDFromBytes(request.getRemoteAddr().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Long perSession=redis.execute(RATE,List.of(key(id,"rate")));
        Long perAddress=redis.execute(RATE,List.of(PREFIX+"address:"+address+":rate"));
        if(perSession==null||perAddress==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"智慧鮮果暫時忙碌，請稍後再試。");
        if(perSession>20||perAddress>100)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"訊息太頻繁，請稍後再試。");
    }
    private List<ChatMessageView> all(String id){
        var raw=redis.opsForList().range(key(id,"messages"),0,-1);
        redis.expire(key(id,"messages"),TTL);redis.expire(key(id,"sequence"),TTL);
        return raw==null?List.of():raw.stream().map(s->json.readValue(s,ChatMessageView.class)).toList();
    }
    public ChatService.MessagePage history(String id,Integer before,Integer after){
        if((before!=null&&before<1)||(after!=null&&after<0)||(before!=null&&after!=null))throw new IllegalArgumentException("訊息編號不正確。");
        var selected=all(id).stream().filter(m->before==null||m.messageId()<before).filter(m->after==null||m.messageId()>after).toList();
        boolean more=selected.size()>50;
        return new ChatService.MessagePage(after==null?selected.subList(Math.max(0,selected.size()-50),selected.size()):selected.subList(0,Math.min(50,selected.size())),more,0);
    }
    public List<ChatMessageView> send(String id,String content,Integer replyId){
        String text=content==null?"":content.strip();
        if(text.isEmpty()||text.codePointCount(0,text.length())>1000)throw new IllegalArgumentException("請輸入 1～1000 字的訊息。");
        ChatMessageView.ReplyView reply=null;
        if(replyId!=null){var target=all(id).stream().filter(m->m.messageId().equals(replyId)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("原訊息已過期，請重新選擇。"));
            reply=new ChatMessageView.ReplyView(target.messageId(),target.senderType(),target.messageContent());}
        String answer=replies.faqReply(text);
        if(answer==null||answer.isBlank())answer="我還沒找到合適的解答，請換個方式詢問，或登入會員聯繫客服。";
        var at=LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        var sent=new ChatMessageView(0,0,1,0,text,1,at,List.of(),replyId,reply);
        var response=new ChatMessageView(0,0,0,0,answer,1,at.plusNanos(1_000_000),List.of());
        var result=redis.execute(APPEND,List.of(key(id,"messages"),key(id,"sequence")),json.writeValueAsString(sent),json.writeValueAsString(response));
        if(result==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"智慧鮮果暫時忙碌，請稍後再試。");
        return result.stream().map(s->json.readValue(s.toString(),ChatMessageView.class)).toList();
    }
    public ChatQuizService.StartView start(String id,Integer quizId){
        var run=quizzes.start(0,quizId);
        redis.opsForValue().set(key(id,"run:"+quizId),json.writeValueAsString(new StoredRun(run.snapshot(),null)),Duration.ofMinutes(30));
        return run.view();
    }
    public ChatQuizService.Result finish(String id,Integer quizId,ChatQuizService.Submission submission){
        if(submission==null||submission.token()==null)throw new IllegalArgumentException("測驗已過期，請重新開始。");
        String runKey=key(id,"run:"+quizId),raw=redis.opsForValue().get(runKey);
        if(raw==null)throw new IllegalArgumentException("測驗已過期，請重新開始。");
        var stored=json.readValue(raw,StoredRun.class);
        var run=ChatQuizService.restore(stored.snapshot());
        if(!run.token.equals(submission.token())||!run.belongsTo(0,quizId))throw new IllegalArgumentException("測驗已過期，請重新開始。");
        if(stored.completed()!=null)return stored.completed();
        var result=quizzes.finishGuest(quizId,run,submission.answers());
        var saved=new ChatQuizService.SavedResult(0,quizId,run.view().name(),LocalDateTime.now(ZoneId.of("Asia/Taipei")),result);
        Long completed=redis.execute(COMPLETE,List.of(runKey,key(id,"quiz-results"),key(id,"quiz-sequence")),raw,
            json.writeValueAsString(new StoredRun(stored.snapshot(),result)),json.writeValueAsString(saved));
        if(!Long.valueOf(1).equals(completed)){
            String latest=redis.opsForValue().get(runKey);
            var updated=latest==null?null:json.readValue(latest,StoredRun.class);
            if(updated==null||!updated.snapshot().token().equals(submission.token())||updated.completed()==null)
                throw new IllegalArgumentException("測驗已更新，請重新開始。");
            return updated.completed();
        }
        return result;
    }
    public ChatQuizService.ResultPage quizHistory(String id,Integer before){
        if(before!=null&&before<1)throw new IllegalArgumentException("測驗紀錄編號不正確。");
        var raw=redis.opsForList().range(key(id,"quiz-results"),0,-1);
        redis.expire(key(id,"quiz-results"),TTL);redis.expire(key(id,"quiz-sequence"),TTL);
        var saved=raw==null?List.<ChatQuizService.SavedResult>of():raw.stream().map(s->json.readValue(s,ChatQuizService.SavedResult.class))
            .filter(s->before==null||s.answerId()<before).toList();
        return new ChatQuizService.ResultPage(saved,false);
    }
}
