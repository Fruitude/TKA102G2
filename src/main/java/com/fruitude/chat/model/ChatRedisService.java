package com.fruitude.chat.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

// 聊天室放在 Redis 的資料：後台對話清單的排序與每位會員的未讀數
// 訊息本體只存在 MySQL；Redis 的資料隨時可以由 MySQL 重新算出來，所以全部設有效期限，過期就重算
// 這裡的方法連不上 Redis 時會丟 DataAccessException，由 ChatService 接住後改查 MySQL
@Service
public class ChatRedisService {

	// ZSet：成員是會員編號，分數是最後一則訊息時間（毫秒）
	private static final String CONVERSATIONS_KEY = "chat:conversations";

	// 字串：會員送來、客服還沒讀的則數；key 後面接會員編號
	private static final String UNREAD_KEY_PREFIX = "chat:unread:staff:";

	// 有效期限：過期後下次讀清單會由 MySQL 重算，直接用 SQL 改過的資料最多這麼久之後就會反映出來
	private static final Duration CACHE_TTL = Duration.ofMinutes(10);

	@Autowired
	private StringRedisTemplate redisTemplate;

	// Redis 裡的一列對話清單：會員編號、最後訊息時間、未讀數
	public record ConversationEntry(Integer memberId, LocalDateTime lastMessageAt, long unreadCount) {
	}

	// 依最後訊息時間由新到舊讀出對話清單；Redis 沒有清單時回傳 null，呼叫端要由 MySQL 重算後呼叫 rebuild
	public List<ConversationEntry> readConversations() {
		Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(CONVERSATIONS_KEY, 0, -1);
		if (tuples == null || tuples.isEmpty()) {
			return null;
		}

		List<Integer> memberIds = new ArrayList<>();
		List<String> unreadKeys = new ArrayList<>();
		List<LocalDateTime> lastMessageTimes = new ArrayList<>();
		for (TypedTuple<String> tuple : tuples) {
			Integer memberId = Integer.valueOf(tuple.getValue());
			memberIds.add(memberId);
			unreadKeys.add(UNREAD_KEY_PREFIX + memberId);
			lastMessageTimes.add(toLocalDateTime(tuple.getScore()));
		}
		// 未讀數一次讀出來，不要每位會員各讀一次；沒有 key 代表 0
		List<String> unreadValues = redisTemplate.opsForValue().multiGet(unreadKeys);

		List<ConversationEntry> entries = new ArrayList<>();
		for (int i = 0; i < memberIds.size(); i++) {
			String unreadValue = unreadValues == null ? null : unreadValues.get(i);
			long unreadCount = unreadValue == null ? 0 : Long.parseLong(unreadValue);
			entries.add(new ConversationEntry(memberIds.get(i), lastMessageTimes.get(i), unreadCount));
		}
		return entries;
	}

	// 用 MySQL 算出來的清單整份蓋掉 Redis 的清單與未讀數
	public void rebuild(List<ChatConversationView> conversations) {
		redisTemplate.delete(CONVERSATIONS_KEY);
		for (ChatConversationView conversation : conversations) {
			redisTemplate.opsForZSet().add(CONVERSATIONS_KEY, String.valueOf(conversation.memberId()),
					toScore(conversation.lastMessageAt()));
			String unreadKey = UNREAD_KEY_PREFIX + conversation.memberId();
			if (conversation.unreadCount() > 0) {
				redisTemplate.opsForValue().set(unreadKey, String.valueOf(conversation.unreadCount()), CACHE_TTL);
			} else {
				redisTemplate.delete(unreadKey);
			}
		}
		redisTemplate.expire(CONVERSATIONS_KEY, CACHE_TTL);
	}

	// 有新訊息：更新這位會員的最後訊息時間，讓他排到清單最上面
	// 清單還不存在時不用處理，下次讀清單會由 MySQL 重算，新訊息已經在 MySQL 裡
	public void touchConversation(Integer memberId, LocalDateTime lastMessageAt) {
		if (Boolean.TRUE.equals(redisTemplate.hasKey(CONVERSATIONS_KEY))) {
			redisTemplate.opsForZSet().add(CONVERSATIONS_KEY, String.valueOf(memberId), toScore(lastMessageAt));
		}
	}

	// 客服已讀：這位會員的未讀數歸零
	public void clearUnread(Integer memberId) {
		redisTemplate.delete(UNREAD_KEY_PREFIX + memberId);
	}

	private double toScore(LocalDateTime dateTime) {
		return dateTime == null ? 0 : dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private LocalDateTime toLocalDateTime(Double score) {
		return score == null ? null
				: LocalDateTime.ofInstant(Instant.ofEpochMilli(score.longValue()), ZoneId.systemDefault());
	}

}
