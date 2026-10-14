package com.fruitude.chat.model;

import java.util.List;

// 送到頁面的一筆建議回覆：罐頭訊息的編號、標題、內容，以及會員訊息命中了哪些關鍵字
public record CannedSuggestionView(Integer cannedId, String title, String messageContent,
		List<String> matchedKeywords) {
}
