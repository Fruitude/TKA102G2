package com.fruitude.chat.model;

// 推播到 /topic/chat/staff 的事件，後台聊天室頁面收到後更新清單與目前開著的對話
// type 是 MESSAGE（有新訊息，message 有值）、READ（客服已讀，message 是 null）、MEMBER_READ（會員已讀客服訊息）
// 或 UNREAD（客服把對話標回未讀，只送員工頻道，message 是 null）
// HIDDEN（客服訊息對會員隱藏）、SHOWN（取消隱藏），員工與會員頻道都送；readThroughId 借放被切換的訊息編號
// message 只有送會員頻道的 SHOWN 有值（讓會員畫面把訊息補回），其餘是 null
public record ChatEvent(String type, Integer memberId, ChatMessageView message,Integer readThroughId) {
    public ChatEvent(String type,Integer memberId,ChatMessageView message){this(type,memberId,message,null);}

	public static final String MESSAGE = "MESSAGE";
	public static final String READ = "READ";
    public static final String MEMBER_READ = "MEMBER_READ";
    public static final String UNREAD = "UNREAD";
    public static final String HIDDEN = "HIDDEN";
    public static final String SHOWN = "SHOWN";

}
