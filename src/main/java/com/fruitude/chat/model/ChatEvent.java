package com.fruitude.chat.model;

// 推播到 /topic/chat/staff 的事件，後台聊天室頁面收到後更新清單與目前開著的對話
// type 是 MESSAGE（有新訊息，message 有值）或 READ（客服已讀，message 是 null）
public record ChatEvent(String type, Integer memberId, ChatMessageView message,Integer readThroughId) {
    public ChatEvent(String type,Integer memberId,ChatMessageView message){this(type,memberId,message,null);}

	public static final String MESSAGE = "MESSAGE";
	public static final String READ = "READ";
    public static final String MEMBER_READ = "MEMBER_READ";

}
