package com.fruitude.chat;

import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.dao.DataAccessResourceFailureException;
import com.fruitude.chat.model.*;
import com.fruitude.chat.controller.FrontChatController;
import com.fruitude.quiz.controller.QuizController;
import com.fruitude.quiz.model.ChatQuizService;

public class GuestChatTest {
    static class MemoryRedis extends StringRedisTemplate {
        final Map<String,String> values=new HashMap<>();
        final Map<String,List<String>> lists=new HashMap<>();
        final Map<String,Duration> expiry=new HashMap<>();
        boolean failed;
        void check(){if(failed)throw new DataAccessResourceFailureException("test outage");}
        long increment(String key){long n=Long.parseLong(values.getOrDefault(key,"0"))+1;values.put(key,String.valueOf(n));return n;}
        void append(String key,String value,int limit){var list=lists.computeIfAbsent(key,k->new ArrayList<>());list.add(value);while(list.size()>limit)list.remove(0);}
        @Override public Boolean expire(String key,Duration ttl){check();expiry.put(key,ttl);return true;}
        @Override public ValueOperations<String,String> opsForValue(){return FrontChatTest.proxy(ValueOperations.class,(name,args)->{
            check();if(name.equals("get"))return values.get(args[0]);
            if(name.equals("set")){values.put((String)args[0],(String)args[1]);expiry.put((String)args[0],(Duration)args[2]);return null;}
            throw new UnsupportedOperationException(name);
        });}
        @Override public ListOperations<String,String> opsForList(){return FrontChatTest.proxy(ListOperations.class,(name,args)->{
            check();if(name.equals("range"))return new ArrayList<>(lists.getOrDefault(args[0],List.of()));throw new UnsupportedOperationException(name);
        });}
        @Override @SuppressWarnings("unchecked") public <T>T execute(RedisScript<T> script,List<String> keys,Object... args){
            check();String lua=script.getScriptAsString();
            if(lua.contains("local out={}")){
                var output=new ArrayList<String>();for(var arg:args){String value=arg.toString().replaceFirst("\"messageId\":0","\"messageId\":"+increment(keys.get(1)));append(keys.get(0),value,100);output.add(value);}
                keys.forEach(k->expiry.put(k,Duration.ofHours(24)));return (T)output;
            }
            if(lua.contains("KEEPTTL")){
                if(!Objects.equals(values.get(keys.get(0)),args[0]))return (T)Long.valueOf(0);
                values.put(keys.get(0),args[1].toString());append(keys.get(1),args[2].toString().replaceFirst("\"answerId\":0","\"answerId\":"+increment(keys.get(2))),20);
                expiry.put(keys.get(1),Duration.ofHours(24));expiry.put(keys.get(2),Duration.ofHours(24));return (T)Long.valueOf(1);
            }
            long n=increment(keys.get(0));if(n==1)expiry.put(keys.get(0),Duration.ofMinutes(1));return (T)Long.valueOf(n);
        }
    }
    static class Fixture {
        final MemoryRedis redis=new MemoryRedis();
        final FrontChatTest.QuizFixture quiz=new FrontChatTest.QuizFixture();
        final GuestChatService guests=new GuestChatService(redis,new CannedReplyService(){@Override public String faqReply(String text){return text.contains("配送")?"一般商品 1～3 個工作天出貨。":null;}},quiz.service);
        Fixture()throws Exception{}
        MockHttpServletRequest request(MockHttpSession session){var request=new MockHttpServletRequest();request.setSession(session);request.setRemoteAddr("127.0.0.1");return request;}
    }
    static <T>T inject(T controller,GuestChatService guests)throws Exception {FrontChatTest.field(controller,"guests",guests);return controller;}
    @Test public void visitorIdentityHistoryAndRepliesAreIsolatedAndExpire()throws Exception {
        var f=new Fixture();var session=new MockHttpSession();var request=f.request(session);String id=f.guests.visitor(request);
        assertEquals(id,f.guests.visitor(request));String other=f.guests.visitor(f.request(new MockHttpSession()));assertNotEquals(id,other);
        var reply=f.guests.send(id,"配送多久",null);assertEquals(2,reply.size());assertEquals(Integer.valueOf(0),reply.get(1).senderType());assertTrue(reply.get(1).messageContent().contains("工作天"));
        assertTrue(reply.get(0).imageIds().isEmpty());assertTrue(f.guests.history(other,null,null).messages().isEmpty());
        var quoted=f.guests.send(id,"謝謝",reply.get(1).messageId());assertEquals(reply.get(1).messageId(),quoted.get(0).reply().messageId());
        assertThrows(IllegalArgumentException.class,()->f.guests.send(other,"冒用回覆",reply.get(1).messageId()));
        for(var key:f.redis.values.keySet())if(!key.contains("rate")&&!key.contains("run:"))assertEquals(Duration.ofHours(24),f.redis.expiry.get(key));
        f.redis.values.remove("chat:guest:v1:"+id+":active");assertNotEquals(id,f.guests.visitor(request));assertEquals(0,f.quiz.store.inserts);
    }
    @Test public void guestApiNeverCreatesMemberMessagesSourcePushOrUnreadAndRejectsUploads()throws Exception {
        var f=new Fixture();var member=new FrontChatTest.Fixture();var controller=inject(new FrontChatController(member.service),f.guests);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();var session=new MockHttpSession();
        assertEquals(200,mvc.perform(get("/api/front/chat/unread").session(session)).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/front/chat/messages").session(session).param("content","配送")).andReturn().getResponse().getStatus());
        var response=mvc.perform(post("/api/front/chat/messages").session(session).header("X-Requested-With","XMLHttpRequest").param("memberId","99").param("productSkuId","11").param("content","配送")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("guestMessages"));
        var image=new MockMultipartFile("images","x.png","image/png",new byte[]{1});
        assertEquals(400,mvc.perform(multipart("/api/front/chat/messages").file(image).session(session).header("X-Requested-With","XMLHttpRequest").param("content","hi")).andReturn().getResponse().getStatus());
        assertEquals(401,mvc.perform(get("/api/front/chat/images/1").session(session)).andReturn().getResponse().getStatus());
        assertTrue(member.data.isEmpty());assertTrue(member.events.isEmpty());assertEquals(0,member.cacheTouches);assertEquals(0,member.commits);
        f.redis.failed=true;assertEquals(503,mvc.perform(get("/api/front/chat/messages").session(session)).andReturn().getResponse().getStatus());assertTrue(member.data.isEmpty());
    }
    @Test public void messageLimitsAreBoundedAndRateLimitRejectsBursts()throws Exception {
        var f=new Fixture();var request=f.request(new MockHttpSession());String id=f.guests.visitor(request);
        assertThrows(IllegalArgumentException.class,()->f.guests.send(id," ",null));assertThrows(IllegalArgumentException.class,()->f.guests.send(id,"果".repeat(1001),null));
        for(int i=0;i<60;i++)f.guests.send(id,"未知問題"+i,null);
        var page=f.guests.history(id,null,null);assertEquals(50,page.messages().size());assertTrue(page.hasMore());assertEquals(100,f.redis.lists.get("chat:guest:v1:"+id+":messages").size());
        assertEquals(50,f.guests.history(id,page.messages().get(0).messageId(),null).messages().size());
        for(int i=0;i<20;i++)f.guests.limit(id,request);
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->f.guests.limit(id,request));assertEquals(429,error.getStatusCode().value());
    }
    @Test public void guestQuizRoundTripReusesRulesSavesOnlyRedisAndRetriesAreIdempotent()throws Exception {
        var f=new Fixture();String id=f.guests.visitor(f.request(new MockHttpSession()));String other=f.guests.visitor(f.request(new MockHttpSession()));
        var started=f.guests.start(id,7);assertFalse(started.toString().contains("scores"));
        var submission=new ChatQuizService.Submission(started.token(),f.quiz.answers("A"));
        assertThrows(IllegalArgumentException.class,()->f.guests.finish(other,7,submission));
        var result=f.guests.finish(id,7,submission);assertEquals(5,result.recommendations().size());assertEquals(result,f.guests.finish(id,7,submission));
        assertEquals(1,f.guests.quizHistory(id,null).results().size());assertTrue(f.guests.quizHistory(other,null).results().isEmpty());assertEquals(0,f.quiz.store.inserts);
        assertTrue(f.guests.history(id,null,null).messages().isEmpty());
        f.guests.start(id,7);assertThrows(IllegalArgumentException.class,()->f.guests.finish(id,7,submission));
        for(int i=0;i<22;i++){var next=f.guests.start(id,7);f.guests.finish(id,7,new ChatQuizService.Submission(next.token(),f.quiz.answers("B")));}
        assertEquals(20,f.guests.quizHistory(id,null).results().size());assertEquals(0,f.quiz.store.inserts);
    }
    @Test public void quizApiAcceptsVisitorsButDoesNotExposeMemberHistoryOrAllowCsrf()throws Exception {
        var f=new Fixture();var mvc=MockMvcBuilders.standaloneSetup(inject(new QuizController(f.quiz.service),f.guests)).build();var session=new MockHttpSession();
        assertEquals(200,mvc.perform(get("/api/front/chat/quizzes").session(session)).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/front/chat/quizzes/7/start").session(session)).andReturn().getResponse().getStatus());
        f.quiz.service.finish(1,7,f.quiz.service.start(1,7),f.quiz.answers("A"));
        var history=mvc.perform(get("/api/front/chat/quizzes/results").session(session).param("memberId","1")).andReturn().getResponse();
        assertEquals(200,history.getStatus());assertTrue(history.getContentAsString().contains("\"results\":[]"));
    }
}
