package com.fruitude.chat.model;
import java.time.LocalDateTime;
import java.util.List;

/** Member-safe message payload. Reply previews never include employee identifiers. */
public record ChatMessageView(Integer messageId,Integer memberId,Integer senderType,Integer messageType,
    String messageContent,Integer isRead,LocalDateTime createdAt,List<Integer> imageIds,
    Integer replyMessageId,ReplyView reply,InquiryProduct inquiryProduct) {
    public record ReplyView(Integer messageId,Integer senderType,String messageContent) {}
    public record InquiryProduct(Integer productId,String name,Integer skuId,String skuName) {}
    public boolean staffOnly(){return Integer.valueOf(Message.SENDER_SYSTEM).equals(senderType)&&messageContent!=null&&messageContent.startsWith("客戶詢問來源\n商品：");}
    public ChatMessageView forMember(){
        if(reply!=null && Integer.valueOf(Message.SENDER_SYSTEM).equals(reply.senderType()) && reply.messageContent()!=null && reply.messageContent().startsWith("客戶詢問來源\n商品："))
            return withReply(new ReplyView(reply.messageId(),null,"此訊息僅供客服查看"));
        return this;
    }

    private static final java.util.regex.Pattern SOURCE=java.util.regex.Pattern.compile(
        "\\A客戶詢問來源\n商品：(.*?)（商品編號 #(\\d+)）\n規格：(.*?)（規格編號 #(\\d+)）\\z",java.util.regex.Pattern.DOTALL);
    private static InquiryProduct inquiry(Message m){
        if(!Integer.valueOf(Message.SENDER_SYSTEM).equals(m.getSenderType())||m.getMessageContent()==null)return null;
        var match=SOURCE.matcher(m.getMessageContent());if(!match.matches())return null;
        try{return new InquiryProduct(Integer.valueOf(match.group(2)),match.group(1),Integer.valueOf(match.group(4)),match.group(3));}
        catch(NumberFormatException invalid){return null;}
    }
    private static String inquiryText(InquiryProduct source){return "客戶詢問來源\n商品："+source.name()+"\n規格："+source.skuName();}
    public ChatMessageView(Integer id,Integer member,Integer sender,Integer type,String content,Integer read,LocalDateTime at,List<Integer> images,Integer replyId,ReplyView reply){
        this(id,member,sender,type,content,read,at,images,replyId,reply,null);
    }

    public ChatMessageView(Integer id,Integer member,Integer sender,Integer type,String content,Integer read,LocalDateTime at,List<Integer> images){
        this(id,member,sender,type,content,read,at,images,null,null);
    }
    public static ChatMessageView from(Message m,List<Integer> images){
        var source=inquiry(m);
        return new ChatMessageView(m.getMessageId(),m.getMemberId(),m.getSenderType(),m.getMessageType(),source==null?m.getMessageContent():inquiryText(source),
            m.getIsRead(),m.getCreatedAt(),images==null?List.of():images,m.getReplyMessageId(),null,source);
    }
    public ChatMessageView withReply(ReplyView value){
        return new ChatMessageView(messageId,memberId,senderType,messageType,messageContent,isRead,createdAt,imageIds,replyMessageId,value,inquiryProduct);
    }
    public static ReplyView preview(Message target){
        var source=inquiry(target);String text=source==null?target.getMessageContent():inquiryText(source);
        if(text==null||text.isBlank())text="[圖片訊息]";
        else {var points=text.strip().codePoints().toArray();text=new String(points,0,Math.min(points.length,100))+(points.length>100?"…":"");}
        return new ReplyView(target.getMessageId(),target.getSenderType(),text);
    }
}
