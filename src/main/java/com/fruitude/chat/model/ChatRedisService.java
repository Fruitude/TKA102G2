package com.fruitude.chat.model;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** One expiring snapshot keeps ordering and unread counts consistent. MySQL remains authoritative. */
@Service
public class ChatRedisService {
    private static final String SNAPSHOT="chat:staff:snapshot:v2",GENERATION="chat:staff:generation:v2";
    private static final DefaultRedisScript<Long> INVALIDATE=new DefaultRedisScript<>(
        "redis.call('SET',KEYS[1],ARGV[1]); redis.call('DEL',KEYS[2]); return 1",Long.class);
    private static final DefaultRedisScript<Long> REBUILD=new DefaultRedisScript<>(
        "if (redis.call('GET',KEYS[1]) or '') == ARGV[1] then redis.call('SET',KEYS[2],ARGV[2],'EX',600); return 1 end; return 0",Long.class);
    @Autowired private StringRedisTemplate redisTemplate;
    public record ConversationEntry(Integer memberId,LocalDateTime lastMessageAt,long unreadCount) {}
    public String generation(){String value=redisTemplate.opsForValue().get(GENERATION);return value==null?"":value;}
    public List<ConversationEntry> readConversations(){
        String value=redisTemplate.opsForValue().get(SNAPSHOT);
        if(value==null)return null;
        var rows=new ArrayList<ConversationEntry>();
        for(String line:value.split("\n"))if(!line.isEmpty()){
            String[] parts=line.split("[|]",-1);
            rows.add(new ConversationEntry(Integer.valueOf(parts[0]),parts[1].isEmpty()?null:LocalDateTime.parse(parts[1]),Long.parseLong(parts[2])));
        }
        return rows;
    }
    public void rebuild(List<ChatConversationView> rows,String generation){
        var value=new StringBuilder();
        for(var row:rows)value.append(row.memberId()).append('|').append(row.lastMessageAt()==null?"":row.lastMessageAt())
            .append('|').append(row.unreadCount()).append('\n');
        // If a commit invalidated the cache while SQL was running, do not publish that stale snapshot.
        redisTemplate.execute(REBUILD,List.of(GENERATION,SNAPSHOT),generation,value.toString());
    }
    public void touchConversation(Integer id,LocalDateTime at){invalidate();}
    public void clearUnread(Integer id){invalidate();}
    private void invalidate(){redisTemplate.execute(INVALIDATE,List.of(GENERATION,SNAPSHOT),UUID.randomUUID().toString());}
}
