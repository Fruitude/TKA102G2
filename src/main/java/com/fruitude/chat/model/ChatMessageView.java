package com.fruitude.chat.model;
import java.time.LocalDateTime;
import java.util.List;

/** Member-safe message payload. Reply previews never include employee identifiers. */
public record ChatMessageView(Integer messageId,Integer memberId,Integer senderType,Integer messageType,
    String messageContent,Integer isRead,LocalDateTime createdAt,List<Integer> imageIds,
    Integer replyMessageId,ReplyView reply) {
    public record ReplyView(Integer messageId,Integer senderType,String messageContent) {}
    public ChatMessageView(Integer id,Integer member,Integer sender,Integer type,String content,Integer read,LocalDateTime at,List<Integer> images){
        this(id,member,sender,type,content,read,at,images,null,null);
    }
    public static ChatMessageView from(Message m,List<Integer> images){
        return new ChatMessageView(m.getMessageId(),m.getMemberId(),m.getSenderType(),m.getMessageType(),m.getMessageContent(),
            m.getIsRead(),m.getCreatedAt(),images==null?List.of():images,m.getReplyMessageId(),null);
    }
    public ChatMessageView withReply(ReplyView value){
        return new ChatMessageView(messageId,memberId,senderType,messageType,messageContent,isRead,createdAt,imageIds,replyMessageId,value);
    }
    public static ReplyView preview(Message target){
        String text=target.getMessageContent();
        if(text==null||text.isBlank())text="[圖片訊息]";
        else {var points=text.strip().codePoints().toArray();text=new String(points,0,Math.min(points.length,100))+(points.length>100?"…":"");}
        return new ReplyView(target.getMessageId(),target.getSenderType(),text);
    }
}
