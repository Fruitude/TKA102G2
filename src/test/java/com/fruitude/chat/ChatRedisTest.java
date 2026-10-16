package com.fruitude.chat;
import java.util.*;
import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.fruitude.chat.model.*;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.dao.DataAccessResourceFailureException;

public class ChatRedisTest {
    static class MemoryRedis extends StringRedisTemplate {
        Map<String,String> data=new HashMap<>();
        @Override public ValueOperations<String,String> opsForValue(){return FrontChatTest.proxy(ValueOperations.class,(name,args)->{
            if(name.equals("get"))return data.get(args[0]);throw new UnsupportedOperationException(name);
        });}
        @Override @SuppressWarnings("unchecked") public <T>T execute(RedisScript<T> script,List<String> keys,Object... args){
            if(script.getScriptAsString().contains("'DEL'")){
                data.put(keys.get(0),(String)args[0]);data.remove(keys.get(1));return (T)Long.valueOf(1);
            }
            if(data.getOrDefault(keys.get(0),"").equals(args[0])){
                data.put(keys.get(1),(String)args[1]);return (T)Long.valueOf(1);
            }return (T)Long.valueOf(0);
        }
    }
    @Test public void snapshotRebuildCannotOverwriteAConcurrentCommittedChange()throws Exception {
        var storage=new MemoryRedis();var cache=new ChatRedisService();FrontChatTest.field(cache,"redisTemplate",storage);
        String previous=cache.generation();cache.touchConversation(1,LocalDateTime.now());
        cache.rebuild(List.of(new ChatConversationView(1,"member",LocalDateTime.now(),8)),previous);assertNull(cache.readConversations());
        cache.rebuild(List.of(new ChatConversationView(1,"member",LocalDateTime.of(2026,10,16,0,0),9)),cache.generation());
        assertEquals(9,cache.readConversations().get(0).unreadCount());cache.clearUnread(1);assertNull(cache.readConversations());
    }
    @Test public void emptyConversationListIsCachedAsAnEmptySnapshot()throws Exception {
        var cache=new ChatRedisService();FrontChatTest.field(cache,"redisTemplate",new MemoryRedis());cache.rebuild(List.of(),cache.generation());assertEquals(List.of(),cache.readConversations());
    }
    @Test public void redisFailureCannotLoseACommittedMessageAndDatabaseFallbackWorks()throws Exception {
        var fixture=new FrontChatTest.Fixture();
        FrontChatTest.field(fixture.service,"chatRedisService",new ChatRedisService(){
            @Override public void touchConversation(Integer id,LocalDateTime time){throw new DataAccessResourceFailureException("test outage");}
            @Override public void clearUnread(Integer id){throw new DataAccessResourceFailureException("test outage");}
            @Override public List<ConversationEntry> readConversations(){throw new DataAccessResourceFailureException("test outage");}
        });
        assertNotNull(fixture.service.sendMemberMessage(1,"保留訊息",null));assertEquals(1,fixture.data.size());assertEquals(2,fixture.events.size());
        assertEquals(List.of(),fixture.service.getConversationList());
    }
}
