package com.fruitude.chat;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.*;
import com.fruitude.chat.model.*;
import com.fruitude.chat.model.Message;
import com.fruitude.chat.controller.*;
import com.fruitude.chat.config.*;

public class FrontChatTest {
    static void field(Object target,String name,Object value)throws Exception {
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,java.util.function.BiFunction<String,Object[],Object> action){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->action.apply(m.getName(),a));
    }
    public static class Fixture {
        public final List<Message> data=new ArrayList<>();public final List<MessageImage> images=new ArrayList<>();
        final List<ChatEvent> events=new ArrayList<>();final List<String> destinations=new ArrayList<>();
        boolean committed;int commits,cacheTouches;public final ChatService service=new ChatService();
        public final MessageRepository repo;
        public Fixture()throws Exception {
            repo=proxy(MessageRepository.class,(name,args)->{
                int member=args!=null&&args.length>0&&args[0] instanceof Integer?(Integer)args[0]:0;
                switch(name){
                case "findById":return data.stream().filter(m->m.getMessageId().equals(args[0])).findFirst();
                case "findAllById":return data.stream().filter(m->((Collection<?>)args[0]).contains(m.getMessageId())).toList();
                case "save": {var m=(Message)args[0];m.setMessageId(data.size()+1);data.add(m);return m;}
                case "findEmployeeNames":return Collections.singletonList(new Object[]{9,"測試客服小林"});
                case "findMemberNames":return Collections.singletonList(new Object[]{1,"測試會員"});
                case "page":case "after": {
                    Integer before=name.equals("page")?(Integer)args[1]:null,after=name.equals("after")?(Integer)args[1]:(Integer)args[2];
                    var result=data.stream().filter(m->m.getMemberId()==member&&m.getStatus()==1)
                        .filter(m->before==null||m.getMessageId()<before).filter(m->after==null||m.getMessageId()>after)
                        .sorted(name.equals("page")?Comparator.comparing(Message::getMessageId).reversed():Comparator.comparing(Message::getMessageId)).limit(51).toList();return result;
                }
                case "findByMemberIdAndStatusOrderByCreatedAtAscMessageIdAsc":return data.stream().filter(m->m.getMemberId()==member&&m.getStatus()==1).toList();
                case "unreadByMember":return data.stream().filter(m->m.getMemberId()==member&&m.getStatus()==1&&m.getSenderType()!=1&&m.getIsRead()==0).count();
                case "lastMemberMessage":return data.stream().filter(m->m.getMemberId()==member&&m.getSenderType()==1&&m.getStatus()==1).map(Message::getMessageId).max(Integer::compareTo).orElse(null);
                case "markStaffMessagesRead":case "markMemberMessagesReadThrough":{
                    int changed=0;for(var m:data)if(m.getMemberId()==member&&m.getStatus()==1&&m.getIsRead()==0&&m.getMessageId()<=(Integer)args[1]
                        && (name.equals("markStaffMessagesRead")?m.getSenderType()!=1:m.getSenderType()==1)){
                        m.setIsRead(1);m.setReadAt((LocalDateTime)args[2]);changed++;}return changed;
                }
                case "findConversationRows":return List.of();
                default:throw new UnsupportedOperationException(name);
                }
            });
            var imageRepo=proxy(MessageImageRepository.class,(name,args)->{
                switch(name){
                case "save":var image=(MessageImage)args[0];image.setImageId(images.size()+1);images.add(image);return image;
                case "findById":return images.stream().filter(i->i.getImageId().equals(args[0])).findFirst();
                case "owned":return images.stream().filter(i->i.getImageId().equals(args[0])).filter(i->data.stream().anyMatch(m->m.getMessageId().equals(i.getMessageId())&&m.getMemberId().equals(args[1])&&m.getStatus()==1)).count();
                case "findByMessageIdInOrderByMessageIdAscSortOrderAsc":return List.of();
                default:throw new UnsupportedOperationException(name);
                }
            });
            var manager=new AbstractPlatformTransactionManager(){
                protected Object doGetTransaction(){committed=false;return new Object();}
                protected void doBegin(Object t,TransactionDefinition d){}
                protected void doCommit(DefaultTransactionStatus s){committed=true;commits++;}
                protected void doRollback(DefaultTransactionStatus s){}
            };
            var redis=new ChatRedisService(){
                @Override public void touchConversation(Integer id,LocalDateTime at){assertTrue(committed);cacheTouches++;}
                @Override public void clearUnread(Integer id){}
            };
            var messaging=new SimpMessagingTemplate((message,timeout)->{
                Object payload=message.getPayload();
                ChatEvent event=payload instanceof ChatEvent e?e:payload instanceof ChatService.StaffEvent e?new ChatEvent(e.type(),e.memberId(),e.message().publicView(),e.readThroughId()):null;
                if(event!=null){assertTrue(committed||!event.type().equals(ChatEvent.MESSAGE));events.add(event);destinations.add(SimpMessageHeaderAccessor.getDestination(message.getHeaders()));}return true;
            });
            field(service,"messageRepository",repo);field(service,"messageImageRepository",imageRepo);
            field(service,"transactionTemplate",new TransactionTemplate(manager));field(service,"chatRedisService",redis);field(service,"messagingTemplate",messaging);
            field(service,"cannedReplyService",new CannedReplyService());
        }
        public Message add(int member,int sender,int status){var m=new Message();m.setMessageId(data.size()+1);m.setMemberId(member);m.setSenderType(sender);m.setStatus(status);m.setIsRead(0);m.setMessageType(0);m.setCreatedAt(LocalDateTime.now());m.setMessageContent("測試訊息");data.add(m);return m;}
    }
    @Test public void apiRequiresLoginAndRejectsFormCsrfAndIgnoresForeignMemberId()throws Exception {
        var f=new Fixture();var mvc=MockMvcBuilders.standaloneSetup(new FrontChatController(f.service)).build();
        assertEquals(401,mvc.perform(get("/api/front/chat/messages")).andReturn().getResponse().getStatus());
        var session=new MockHttpSession();session.setAttribute("loggedInMemberId",1);
        assertEquals(403,mvc.perform(post("/api/front/chat/messages").session(session).param("content","hi")).andReturn().getResponse().getStatus());
        var response=mvc.perform(post("/api/front/chat/messages").session(session).header("X-Requested-With","XMLHttpRequest").param("memberId","999").param("senderType","2").param("content","hello")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertEquals(Integer.valueOf(1),f.data.get(0).getMemberId());assertEquals(Integer.valueOf(1),f.data.get(0).getSenderType());assertNull(f.data.get(0).getEmployeeId());
        assertEquals(403,mvc.perform(post("/api/front/chat/messages").session(session).header("X-Requested-With","XMLHttpRequest").header("Sec-Fetch-Site","cross-site").param("content","hi")).andReturn().getResponse().getStatus());
    }
    @Test public void sendsCommitBeforeCacheAndBothTopicsAndNeverPublishInvalidImage()throws Exception {
        var f=new Fixture();var message=f.service.sendMemberMessage(1,"  hello  ",null);
        assertEquals(1,f.commits);assertEquals(1,f.cacheTouches);assertEquals("hello",message.messageContent());
        assertEquals(List.of(ChatService.STAFF_TOPIC,ChatService.memberTopic(1)),f.destinations);
        try{f.service.sendMemberMessage(1,"",List.of(new MockMultipartFile("images","fake.png","image/png","<script>bad</script>".getBytes())));fail();}catch(IllegalArgumentException expected){}
        assertEquals(1,f.data.size());assertEquals(2,f.events.size());
    }
    @Test public void serverDetectsImageBytesUsingProductRulesAndRejectsForeignImages()throws Exception {
        var f=new Fixture();var bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=");
        var upload=new MockMultipartFile("images","wrong.txt","text/plain",bytes);
        var result=f.service.sendMemberMessage(1,"",List.of(upload));assertEquals(Integer.valueOf(1),result.messageType());
        assertEquals("image/png",f.images.get(0).getImageType());assertNotNull(f.service.getMemberImage(1,1,false));assertNull(f.service.getMemberImage(2,1,false));
        f.data.get(0).setStatus(0);assertNull(f.service.getMemberImage(1,1,false));
    }
    @Test public void historyAndReconnectUseBoundedBatchesWithoutForeignOrHiddenRows()throws Exception {
        var f=new Fixture();for(int i=0;i<125;i++)f.add(1,2,1);f.add(2,2,1);f.add(1,2,0);
        var latest=f.service.getMemberConversation(1,null,null);assertEquals(50,latest.messages().size());assertEquals(Integer.valueOf(76),latest.messages().get(0).messageId());assertTrue(latest.hasMore());
        var first=f.service.getMemberConversation(1,null,0);assertEquals(Integer.valueOf(1),first.messages().get(0).messageId());assertEquals(Integer.valueOf(50),first.messages().get(49).messageId());
        var older=f.service.getMemberConversation(1,76,null);assertEquals(Integer.valueOf(26),older.messages().get(0).messageId());
        var tail=f.service.getMemberConversation(1,null,100);assertEquals(25,tail.messages().size());assertFalse(tail.hasMore());
        try{f.service.getMemberConversation(1,0,null);fail();}catch(IllegalArgumentException expected){}
        try{f.service.getMemberConversation(1,2,3);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void readOnlyMarksVisibleWatermarkAndOwnIncomingMessages()throws Exception {
        var f=new Fixture();f.add(1,2,1);f.add(1,1,1);f.add(2,2,1);f.add(1,2,1);
        f.service.markReadByMember(1,1);assertEquals(1,f.service.memberUnread(1));assertEquals(Integer.valueOf(0),f.data.get(1).getIsRead());assertEquals(Integer.valueOf(0),f.data.get(2).getIsRead());
        f.service.markReadByStaff(1);assertEquals(Integer.valueOf(1),f.data.get(1).getIsRead());assertEquals(Integer.valueOf(2),f.events.get(f.events.size()-1).readThroughId());
    }
    static class Registration extends ChannelRegistration {public List<ChannelInterceptor> list(){return getInterceptors();}}
    static org.springframework.messaging.Message<byte[]> frame(StompCommand cmd,Map<String,Object> attrs,String destination){
        var h=StompHeaderAccessor.create(cmd);h.setSessionId("test");h.setSessionAttributes(attrs);if(destination!=null)h.setDestination(destination);return MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());
    }
    @Test public void websocketCannotSubscribeToAnotherMemberOrStaffOrSendAndStopsAfterLogout(){
        var config=new ChatWebSocketConfig();var registration=new Registration();config.configureClientInboundChannel(registration);var interceptor=registration.list().get(0);
        var session=new MockHttpSession();session.setAttribute("loggedInMemberId",1);Map<String,Object> attrs=Map.of("memberId",1,"chatSession",session);
        interceptor.preSend(frame(StompCommand.CONNECT,attrs,null),null);
        assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE,attrs,ChatService.memberTopic(1)),null));
        for(String dest:List.of(ChatService.STAFF_TOPIC,ChatService.memberTopic(2)))try{interceptor.preSend(frame(StompCommand.SUBSCRIBE,attrs,dest),null);fail();}catch(MessagingException expected){}
        try{interceptor.preSend(frame(StompCommand.SEND,attrs,ChatService.memberTopic(1)),null);fail();}catch(MessagingException expected){}
        var outbound=new Registration();config.configureClientOutboundChannel(outbound);var headers=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);headers.setSessionId("test");
        var message=MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());assertNotNull(outbound.list().get(0).preSend(message,null));session.invalidate();assertNull(outbound.list().get(0).preSend(message,null));
    }
    @Test public void staffSubscriptionIsPreservedButCannotSubscribeToMemberChannel(){
        var config=new ChatWebSocketConfig();var reg=new Registration();config.configureClientInboundChannel(reg);var in=reg.list().get(0);
        var session=new MockHttpSession();session.setAttribute("loggedInEmployeeId",9);Map<String,Object> attrs=Map.of("employeeId",9,"chatSession",session);
        assertNotNull(in.preSend(frame(StompCommand.SUBSCRIBE,attrs,ChatService.STAFF_TOPIC),null));
        try{in.preSend(frame(StompCommand.SUBSCRIBE,attrs,ChatService.memberTopic(1)),null);fail();}catch(MessagingException expected){}
    }
}
