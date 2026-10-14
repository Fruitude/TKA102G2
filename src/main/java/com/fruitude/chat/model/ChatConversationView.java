package com.fruitude.chat.model;

import java.time.LocalDateTime;

// 後台對話清單的一列：哪位會員、最後一則訊息的時間、會員送來還沒讀的則數
// memberName 在會員資料被刪除時是 null
public record ChatConversationView(Integer memberId, String memberName, LocalDateTime lastMessageAt,
		long unreadCount) {
}
