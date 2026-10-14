package com.fruitude.chat.model;

import java.time.LocalDateTime;
import java.util.List;

// 送到頁面的一則訊息：刻意不放 employeeId，員工的訊息在頁面上只顯示「客服」
// imageIds 是這則訊息的圖片編號（依 sort_order），頁面用它組圖片網址；沒有圖片時是空的清單
public record ChatMessageView(Integer messageId, Integer memberId, Integer senderType, Integer messageType,
		String messageContent, Integer isRead, LocalDateTime createdAt, List<Integer> imageIds) {

	public static ChatMessageView from(Message message, List<Integer> imageIds) {
		return new ChatMessageView(message.getMessageId(), message.getMemberId(), message.getSenderType(),
				message.getMessageType(), message.getMessageContent(), message.getIsRead(), message.getCreatedAt(),
				imageIds == null ? List.of() : imageIds);
	}

}
