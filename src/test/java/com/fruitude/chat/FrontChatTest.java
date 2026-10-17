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
                case "findInquirySku":return Integer.valueOf(11).equals(args[0])?Collections.singletonList(new Object[]{24,"測試芒果",11,"5顆盒裝"}):Integer.valueOf(12).equals(args[0])?Collections.singletonList(new Object[]{25,"測試柚子",12,"2顆盒裝"}):List.of();
                case "findById":return data.stream().filter(m->m.getMessageId().equals(args[0])).findFirst();
                case "findAllById":return data.stream().filter(m->((Collection<?>)args[0]).contains(m.getMessageId())).toList();
                case "save": {var m=(Message)args[0];m.setMessageId(data.size()+1);data.add(m);return m;}
                case "findEmployeeNames":return Collections.singletonList(new Object[]{9,"測試客服小林"});
                case "findMemberNames":return Collections.singletonList(new Object[]{1,"測試會員"});
                case "page":case "after": {
                    Integer before=name.equals("page")?(Integer)args[1]:null,after=name.equals("after")?(Integer)args[1]:(Integer)args[2];
                    var result=data.stream().filter(m->m.getMemberId()==member&&m.getStatus()==1)
                        .filter(m->!ChatMessageView.from(m,List.of()).staffOnly()).filter(m->before==null||m.getMessageId()<before).filter(m->after==null||m.getMessageId()>after)
                        .sorted(name.equals("page")?Comparator.comparing(Message::getMessageId).reversed():Comparator.comparing(Message::getMessageId)).limit(51).toList();return result;
                }
                case "findByMemberIdAndStatusOrderByCreatedAtAscMessageIdAsc":return data.stream().filter(m->m.getMemberId()==member&&m.getStatus()==1).toList();
                case "unreadByMember":return data.stream().filter(m->m.getMemberId()==member&&m.getStatus()==1&&m.getSenderType()!=1&&m.getIsRead()==0&&!ChatMessageView.from(m,List.of()).staffOnly()).count();
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
            field(service,"cannedReplyService",new CannedReplyService(){@Override public String faqReply(String text){return null;}});
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
    @Test public void welcomeTokenIsStableUntilMemberLogsInAgainWithoutChangingEmployeeSession()throws Exception {
        var f=new Fixture();var mvc=MockMvcBuilders.standaloneSetup(new FrontChatController(f.service)).build();var session=new MockHttpSession();session.setAttribute("loggedInMemberId",1);session.setAttribute("loggedInEmployeeId",9);
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var first=json.readValue(mvc.perform(get("/api/front/chat/unread").session(session)).andReturn().getResponse().getContentAsString(),Map.class);
        var second=json.readValue(mvc.perform(get("/api/front/chat/unread").session(session)).andReturn().getResponse().getContentAsString(),Map.class);assertEquals(first.get("welcomeToken"),second.get("welcomeToken"));
        var listener=new ChatWebSocketConfig().chatLoginListener();session.removeAttribute("loggedInMemberId");listener.attributeRemoved(new jakarta.servlet.http.HttpSessionBindingEvent(session,"loggedInMemberId",1));
        assertEquals(401,mvc.perform(get("/api/front/chat/unread").session(session)).andReturn().getResponse().getStatus());
        session.setAttribute("loggedInMemberId",1);listener.attributeAdded(new jakarta.servlet.http.HttpSessionBindingEvent(session,"loggedInMemberId",1));
        var again=json.readValue(mvc.perform(get("/api/front/chat/unread").session(session)).andReturn().getResponse().getContentAsString(),Map.class);assertNotEquals(first.get("welcomeToken"),again.get("welcomeToken"));assertEquals(9,session.getAttribute("loggedInEmployeeId"));assertTrue(f.data.isEmpty());
        var token=session.getAttribute("chat.welcome.token");listener.attributeReplaced(new jakarta.servlet.http.HttpSessionBindingEvent(session,"loggedInMemberId",1));assertNotEquals(token,session.getAttribute("chat.welcome.token"));
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

    static CannedReplyService faqFixture(String[][] rows, List<com.fruitude.promo.model.PromoProject> promos)throws Exception {
        var service=new CannedReplyService();
        field(service,"faqJdbc",new org.springframework.jdbc.core.JdbcTemplate(){
            @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper){
                assertTrue(sql.contains("WITH RECURSIVE active_categories"));assertTrue(sql.contains("parent_category_id IS NULL AND status=1"));assertTrue(sql.contains("WHERE c.status=1"));
                var result=new ArrayList<T>();
                for(var row:rows)try{result.add(mapper.mapRow(proxy(java.sql.ResultSet.class,(name,args)->{
                    if(name.equals("getString"))return row[switch((String)args[0]){case "question"->0;case "answer"->1;default->2;}];throw new UnsupportedOperationException(name);
                }),result.size()));}catch(java.sql.SQLException error){throw new RuntimeException(error);}return result;
            }
        });
        field(service,"faqPromos",proxy(com.fruitude.promo.model.PromoRepository.class,(name,args)->promos));return service;
    }
    @Test public void faqChoosesSpecificCompoundKeywordsAndNormalizesSpaces()throws Exception {
        var faq=faqFixture(new String[][]{{"普通","一般生日說明","生日優惠"},{"無法使用","請確認生日月份與今年使用次數","生日&不能用,生日優惠&不能"}},List.of());
        assertEquals("請確認生日月份與今年使用次數",faq.faqReply("生日 優惠不能用了"));
        assertEquals("一般生日說明",faq.faqReply("想詢問生日優惠"));
        assertNull(faq.faqReply("這張圖片很好看"));assertNull(faq.faqReply(null));assertNull(faq.faqReply("  "));
    }
    @Test public void faqOffersFollowLivePromotionsAndDoNotKeepExpiredDiscounts()throws Exception {
        var promos=new ArrayList<com.fruitude.promo.model.PromoProject>();var promo=new com.fruitude.promo.model.PromoProject();
        promo.setPromoProjectTitle("壽星優惠");promo.setPromoType("BIRTHDAY_MONTH");promo.setBenefitType("PERCENT_OFF");promo.setBenefitValue(70);promos.add(promo);
        var faq=faqFixture(new String[][]{{"生日優惠是幾折？","舊的固定說明","生日折扣,生日&幾折"}},promos);
        assertTrue(faq.faqReply("生日折扣幾折").contains("7 折"));promo.setBenefitValue(80);assertTrue(faq.faqReply("生日折扣").contains("8 折"));
        promos.clear();assertFalse(faq.faqReply("生日幾折").contains("7 折"));assertTrue(faq.faqReply("生日幾折").contains("目前沒有"));
    }
    @Test public void automaticFaqReplyCommitsWithMemberMessageAndDoesNotReadItForStaff()throws Exception {
        var f=new Fixture();field(f.service,"cannedReplyService",new CannedReplyService(){@Override public String faqReply(String text){return "本商城付款以信用卡為主。";}});
        var sent=f.service.sendMemberMessage(1,"付款方式？",null);assertEquals(1,f.commits);assertEquals(2,f.data.size());
        var member=f.data.get(0);var system=f.data.get(1);assertEquals(Message.SENDER_MEMBER,member.getSenderType().intValue());assertEquals(Message.UNREAD,member.getIsRead().intValue());
        assertEquals(Message.SENDER_SYSTEM,system.getSenderType().intValue());assertEquals(sent.messageId(),system.getReplyMessageId());assertNull(system.getEmployeeId());assertEquals(Message.UNREAD,system.getIsRead().intValue());
        assertEquals(4,f.events.size());assertEquals(system.getMessageId(),f.events.get(2).message().messageId());assertEquals(sent.messageId(),f.events.get(2).message().reply().messageId());
    }
    @Test public void faqNeverAutoRepliesToStaffOrImageOnlyMessages()throws Exception {
        var f=new Fixture();int[] calls={0};field(f.service,"cannedReplyService",new CannedReplyService(){@Override public String faqReply(String text){calls[0]++;return "回答";}});
        f.service.sendStaffMessage(1,9,"付款方式",null,null);assertEquals(0,calls[0]);
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=");
        f.service.sendMemberMessage(1,"",List.of(new MockMultipartFile("images","image.png","image/png",png)));assertEquals(0,calls[0]);assertEquals(2,f.data.size());
    }
    @Test public void faqFailureDoesNotLoseMemberMessageOrCreateAnAnswer()throws Exception {
        var f=new Fixture();field(f.service,"cannedReplyService",new CannedReplyService(){@Override public String faqReply(String text){throw new org.springframework.dao.DataAccessResourceFailureException("FAQ unavailable");}});
        assertNotNull(f.service.sendMemberMessage(1,"活動",null));assertEquals(1,f.data.size());assertEquals(1,f.commits);assertEquals(2,f.events.size());
    }
    @Test public void faqStableTieAndAnswerLengthLimit()throws Exception {
        var faq=faqFixture(new String[][]{{"第一筆","a".repeat(1200),"APPLE"},{"第二筆","另一則","apple"}},List.of());
        String result=faq.faqReply("ＡＰＰＬＥ");assertTrue(result.startsWith("aaa"));assertTrue(result.length()<=1000);assertTrue(result.endsWith("…"));
    }

    @Test public void productInquirySourceIsStoredAndPushedBeforeQuestionWithoutAddingUnread()throws Exception {
        var f=new Fixture();var sent=f.service.sendMemberMessage(1,"這好吃嗎",null,null,11);
        assertEquals(2,f.data.size());assertEquals(1,f.commits);
        var context=f.data.get(0);assertEquals(Message.SENDER_SYSTEM,context.getSenderType().intValue());assertEquals(Message.READ,context.getIsRead().intValue());
        assertTrue(context.getMessageContent().contains("測試芒果"));assertTrue(context.getMessageContent().contains("5顆盒裝"));
        assertEquals("這好吃嗎",sent.messageContent());assertEquals(Message.UNREAD,f.data.get(1).getIsRead().intValue());
        assertEquals(3,f.events.size());assertEquals(context.getMessageId(),f.events.get(0).message().messageId());assertEquals(sent.messageId(),f.events.get(1).message().messageId());
        assertEquals(List.of(ChatService.STAFF_TOPIC,ChatService.STAFF_TOPIC,ChatService.memberTopic(1)),f.destinations);
        var history=f.service.getMemberConversation(1,null,null);assertEquals(1,history.messages().size());assertEquals(0,history.unreadCount());
        assertEquals(sent.messageId(),history.messages().get(0).messageId());
        var sourceView=f.service.getStaffConversation(1).get(0).publicView();assertNotNull(sourceView.inquiryProduct());assertEquals(Integer.valueOf(24),sourceView.inquiryProduct().productId());
        assertFalse(sourceView.messageContent().contains("商品編號"));assertFalse(sourceView.messageContent().contains("規格編號"));
        assertEquals("測試芒果",sourceView.inquiryProduct().name());assertEquals("5顆盒裝",sourceView.inquiryProduct().skuName());
        assertEquals(sourceView.inquiryProduct(),f.service.getStaffConversation(1).get(0).inquiryProduct());

        f.service.sendMemberMessage(1,"甜不甜",null,null,12);assertTrue(f.data.get(2).getMessageContent().contains("測試柚子"));assertTrue(f.data.get(2).getMessageContent().contains("2顆盒裝"));
    }
    @Test public void productInquiryNamesComeFromServerAndInvalidOrMissingSkuDoesNotForgeSource()throws Exception {
        var f=new Fixture();var mvc=MockMvcBuilders.standaloneSetup(new FrontChatController(f.service)).build();var session=new MockHttpSession();session.setAttribute("loggedInMemberId",1);
        var response=mvc.perform(post("/api/front/chat/messages").session(session).header("X-Requested-With","XMLHttpRequest")
            .param("content","想詢問這個商品").param("productSkuId","11").param("productName","偽造商品名")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertTrue(f.data.get(0).getMessageContent().contains("測試芒果"));assertFalse(f.data.get(0).getMessageContent().contains("偽造商品名"));
        f.service.sendMemberMessage(1,"來源不存在時仍能發問",null,null,999);assertEquals(3,f.data.size());
        try{f.service.sendMemberMessage(1,"無效來源",null,null,-1);fail();}catch(IllegalArgumentException expected){}assertEquals(3,f.data.size());
    }
    @Test public void productSourceAndFaqReplyShareCommitAndKeepOriginalReply()throws Exception {
        var f=new Fixture();var original=f.add(1,Message.SENDER_EMPLOYEE,1);field(f.service,"cannedReplyService",new CannedReplyService(){@Override public String faqReply(String text){return "目前優惠";}});
        var sent=f.service.sendMemberMessage(1,"生日優惠",null,original.getMessageId(),11);assertEquals(4,f.data.size());assertEquals(1,f.commits);
        assertEquals(original.getMessageId(),sent.replyMessageId());assertEquals(sent.messageId(),f.data.get(3).getReplyMessageId());assertEquals(Message.UNREAD,f.data.get(2).getIsRead().intValue());assertEquals(5,f.events.size());
    }

    @Test public void inquiryCardMetadataIsNotParsedFromMemberTextAndSurvivesReplyMapping(){
        var m=new Message();m.setMessageId(1);m.setMemberId(1);m.setSenderType(Message.SENDER_SYSTEM);m.setMessageContent("客戶詢問來源\n商品：紅心芭樂（商品編號 #12）\n規格：6顆精選禮盒（約4斤）（規格編號 #22）");
        var view=ChatMessageView.from(m,List.of());assertNotNull(view.inquiryProduct());assertEquals(Integer.valueOf(12),view.inquiryProduct().productId());
        assertEquals("客戶詢問來源\n商品：紅心芭樂\n規格：6顆精選禮盒（約4斤）",view.messageContent());
        assertEquals(view.inquiryProduct(),view.withReply(new ChatMessageView.ReplyView(2,1,"問題")).inquiryProduct());
        assertFalse(ChatMessageView.preview(m).messageContent().contains("編號"));
        m.setSenderType(Message.SENDER_MEMBER);assertNull(ChatMessageView.from(m,List.of()).inquiryProduct());
        m.setSenderType(Message.SENDER_SYSTEM);m.setMessageContent("一般 FAQ 系統回覆");assertNull(ChatMessageView.from(m,List.of()).inquiryProduct());
    }

    @Test public void memberHistoryPaginationAndUnreadExcludeStaffOnlySourceRows()throws Exception {
        var f=new Fixture();
        for(int i=0;i<70;i++){var source=f.add(1,Message.SENDER_SYSTEM,1);source.setMessageContent("客戶詢問來源\n商品：芒果（商品編號 #24）\n規格：盒裝（規格編號 #11）");f.add(1,Message.SENDER_MEMBER,1);}
        var page=f.service.getMemberConversation(1,null,null);assertEquals(50,page.messages().size());assertTrue(page.hasMore());assertEquals(0,page.unreadCount());
        assertTrue(page.messages().stream().noneMatch(ChatMessageView::staffOnly));
        var older=f.service.getMemberConversation(1,page.messages().get(0).messageId(),null);assertEquals(20,older.messages().size());assertFalse(older.hasMore());
        var after=f.service.getMemberConversation(1,null,100);assertEquals(20,after.messages().size());
        assertEquals(140,f.service.getStaffConversation(1).size());
    }
    @Test public void memberCannotReplyToStaffOnlySourceAndStaffQuoteIsRedactedForMember()throws Exception {
        var f=new Fixture();var sent=f.service.sendMemberMessage(1,"這好吃嗎",null,null,11);var source=f.data.get(0);
        try{f.service.sendMemberMessage(1,"嘗試引用來源",null,source.getMessageId());fail();}catch(IllegalArgumentException expected){}
        assertEquals(2,f.data.size());
        f.service.sendStaffMessage(1,9,"收到，我協助確認",null,null,source.getMessageId());
        var last=f.events.get(f.events.size()-1).message();assertEquals("此訊息僅供客服查看",last.reply().messageContent());
        var history=f.service.getMemberConversation(1,null,null);assertEquals(2,history.messages().size());assertEquals("此訊息僅供客服查看",history.messages().get(1).reply().messageContent());
        assertTrue(f.service.getStaffConversation(1).get(2).reply().messageContent().startsWith("客戶詢問來源"));
    }

    static class QuizJdbc extends org.springframework.jdbc.core.JdbcTemplate {
        int inserts;Object[] saved;boolean enabled=true;final List<Map<String,Object>> history=new ArrayList<>();final Map<Integer,String> themeOptions=new HashMap<>();final Map<Integer,String[]> themeFruits=new HashMap<>();
        final String options="{\"A\":{\"text\":\"安靜放空\",\"score\":{\"banana\":3,\"watermelon\":5,\"strawberry\":1,\"guava\":2,\"mango\":4,\"pineapple\":2}},\"B\":{\"text\":\"溫暖小聚\",\"score\":{\"banana\":1,\"watermelon\":1,\"strawberry\":5,\"guava\":1,\"mango\":1,\"pineapple\":1}}}";
        @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper){return query(sql,mapper,new Object[0]);}
        @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object...args){
            var rows=new ArrayList<Map<String,Object>>();
            if(sql.startsWith("SELECT q.quiz_id")){if(enabled)for(int id=7;id<=9;id++)rows.add(Map.of("quiz_id",id,"quiz_name","測試心情","quiz_desc","五題測驗"));}
            else if(sql.startsWith("SELECT question_id")){for(int i=1;i<=5;i++)rows.add(Map.of("question_id",i,"question_text","心情問題 "+i,"options",themeOptions.getOrDefault((Integer)args[0],options)));}
            else if(sql.startsWith("SELECT result_id")){String[] names=themeFruits.getOrDefault((Integer)args[0],new String[]{"香蕉","西瓜","草莓","芭樂","芒果","鳳梨"});for(int i=0;i<names.length;i++)rows.add(Map.of("result_id",i+1,"result_name",names[i],"result_desc","果物靈感 "+names[i]));}
            else if(sql.startsWith("SELECT a.answer_id")){history.stream().filter(r->r.get("member_id").equals(args[0])&&((Integer)r.get("answer_id"))<((Integer)args[1])).sorted(Comparator.comparingInt((Map<String,Object> r)->(Integer)r.get("answer_id")).reversed()).limit(21).forEach(rows::add);}
            else throw new UnsupportedOperationException(sql);
            var result=new ArrayList<T>();for(var row:rows)try{result.add(mapper.mapRow(proxy(java.sql.ResultSet.class,(name,a)->{
                if(name.equals("getTimestamp"))return java.sql.Timestamp.valueOf((java.time.LocalDateTime)row.get((String)a[0]));if(name.equals("getInt"))return ((Number)row.get((String)a[0])).intValue();if(name.equals("getString"))return (String)row.get((String)a[0]);throw new UnsupportedOperationException(name);}),result.size()));}catch(java.sql.SQLException error){throw new RuntimeException(error);}return result;
        }
        @Override @SuppressWarnings("unchecked") public <T>T queryForObject(String sql,Class<T> type,Object...args){return (T)Integer.valueOf(enabled?1:0);}
        @Override public int update(String sql,Object...args){assertTrue(sql.startsWith("INSERT INTO chat_quiz_answer"));inserts++;saved=args;history.add(Map.of("answer_id",inserts,"member_id",args[0],"quiz_id",args[1],"quiz_name","測試心情","score_data",args[3],"created_at",args[5],"result_name","西瓜","result_desc","早期結果"));return 1;}
    }
    static class QuizFixture {
        final QuizJdbc jdbc=new QuizJdbc();final List<com.fruitude.product.model.FrontCatalogService.ProductView> products=new ArrayList<>();final ChatQuizService service;
        QuizFixture(){String[] names={"香蕉","西瓜","草莓","芭樂","芒果","鳳梨"};for(int i=0;i<names.length;i++){
            var sku=new com.fruitude.product.model.FrontCatalogService.SkuView(i+1,"盒裝",100+i,null,20,null,List.of(),1);
            products.add(new com.fruitude.product.model.FrontCatalogService.ProductView(i+1,names[i],"果物",100+i,null,i+1,null,false,List.of(sku),20,4.5));}
            service=new ChatQuizService(jdbc,new com.fruitude.product.model.FrontCatalogService((com.fruitude.product.model.ProductRepository)null){@Override public List<ProductView> getProducts(){return products;}});
        }
        Map<Integer,String> answers(String key){var result=new LinkedHashMap<Integer,String>();for(int i=1;i<=5;i++)result.put(i,key);return result;}
    }
    @Test public void quizScoresAreHiddenAndSummedOnServerWithRankedRecommendations(){
        var f=new QuizFixture();var run=f.service.start(1,7);var json=tools.jackson.databind.json.JsonMapper.builder().build();String publicJson=json.writeValueAsString(run.view());
        assertEquals(5,run.view().questions().size());assertFalse(publicJson.contains("score"));assertFalse(publicJson.contains("banana"));
        var result=f.service.finish(1,7,run,f.answers("A"));assertEquals(5,result.recommendations().size());assertEquals("西瓜",result.recommendations().get(0).fruitName());
        assertEquals(5,result.recommendations().stream().map(ChatQuizService.Recommendation::fruitName).distinct().count());
        var scores=json.readValue((String)f.jdbc.saved[3],Map.class);assertEquals(25,((Number)scores.get("watermelon")).intValue());assertEquals(15,((Number)scores.get("banana")).intValue());assertEquals(1,f.jdbc.inserts);
        assertEquals("草莓",f.service.finish(1,7,f.service.start(1,7),f.answers("B")).recommendations().get(0).fruitName());
    }
    private static void addQuizProduct(QuizFixture f,int id,String name,int status,int stock,double rating){
        var sku=new com.fruitude.product.model.FrontCatalogService.SkuView(id,"盒裝",100,null,stock,null,List.of(),status);
        f.products.add(new com.fruitude.product.model.FrontCatalogService.ProductView(id,name,"果物",100,null,id,null,false,List.of(sku),stock,rating));
    }
    @Test public void quizFillsFiveProductsFromHighestScoringFruitBeforeAnyOtherFruit(){
        var f=new QuizFixture();for(int i=10;i<16;i++)addQuizProduct(f,i,"西瓜禮盒 "+i,1,20,4.9);addQuizProduct(f,30,"芒果高評分",1,20,5.0);
        var result=f.service.finish(1,7,f.service.start(1,7),f.answers("A"));
        assertEquals(5,result.recommendations().size());assertTrue(result.recommendations().stream().allMatch(r->r.fruitName().equals("西瓜")));
        assertEquals(List.of(10,11,12,13,14),result.recommendations().stream().map(ChatQuizService.Recommendation::productId).toList());
    }
    @Test public void quizOnlyFallsBackToLowerScoresWhenHigherFruitCannotFillFive(){
        var f=new QuizFixture();addQuizProduct(f,10,"小玉西瓜",1,20,4.9);addQuizProduct(f,11,"芒果鮮果盒",1,20,4.8);addQuizProduct(f,12,"香蕉鮮果盒",1,20,5.0);
        addQuizProduct(f,13,"西瓜下架",0,20,5.0);addQuizProduct(f,14,"西瓜缺貨",1,0,5.0);
        f.products.add(f.products.stream().filter(p->p.productId()==10).findFirst().orElseThrow());
        var result=f.service.finish(1,7,f.service.start(1,7),f.answers("A"));
        assertEquals(List.of("西瓜","西瓜","芒果","芒果","香蕉"),result.recommendations().stream().map(ChatQuizService.Recommendation::fruitName).toList());
        assertEquals(List.of(10,2,11,5,12),result.recommendations().stream().map(ChatQuizService.Recommendation::productId).toList());
        assertEquals(5,result.recommendations().stream().map(ChatQuizService.Recommendation::productId).distinct().count());
    }
    @Test public void quizReturnsAvailableProductsWhenFewerThanFiveAndRejectsEmptyCatalog(){
        var f=new QuizFixture();f.products.removeIf(p->!p.name().equals("西瓜"));
        assertEquals(1,f.service.finish(1,7,f.service.start(1,7),f.answers("A")).recommendations().size());
        f.products.clear();try{f.service.finish(1,7,f.service.start(1,7),f.answers("A"));fail();}catch(IllegalArgumentException expected){}assertEquals(1,f.jdbc.inserts);
    }
    @Test public void fixedThemePoolsDoNotOverlapEvenForFirstTimeMembersAndIgnoreForeignScores(){
        var f=new QuizFixture();f.products.clear();String[][] pools={{"西瓜","哈密瓜"},{"芒果","鳳梨"},{"蘋果","芭樂"}};
        String[] keys={"watermelon","mango","apple"};var sets=new ArrayList<Set<String>>();
        for(int theme=7;theme<=9;theme++){
            f.jdbc.themeFruits.put(theme,pools[theme-7]);
            f.jdbc.themeOptions.put(theme,"{\"A\":{\"text\":\"安靜\",\"score\":{\""+keys[theme-7]+"\":5,\"strawberry\":5}},\"B\":{\"text\":\"活力\",\"score\":{\""+keys[theme-7]+"\":3}}}");
            for(int i=0;i<6;i++)addQuizProduct(f,theme*100+i,pools[theme-7][0]+"禮盒 "+i,1,20,4.9);
        }
        addQuizProduct(f,999,"草莓不屬於上述主題",1,20,5.0);
        for(int theme:List.of(9,7,8)){
            var result=f.service.finish(theme,theme,f.service.start(theme,theme),f.answers("A"));
            assertEquals(5,result.recommendations().size());assertTrue(result.recommendations().stream().allMatch(r->r.fruitName().equals(pools[theme-7][0])));
            var fruits=result.recommendations().stream().map(ChatQuizService.Recommendation::fruitName).collect(java.util.stream.Collectors.toSet());
            for(var prior:sets)assertTrue(Collections.disjoint(prior,fruits));sets.add(fruits);
            assertEquals(result,f.service.finish(theme,theme,f.service.start(theme,theme),f.answers("A")));
        }
    }
    @Test public void extendedFruitNamesMatchCatalogWithoutConfusingPearAndPineapple(){
        var f=new QuizFixture();f.products.clear();f.jdbc.themeFruits.put(7,new String[]{"梨","柿子","香蕉","蓮霧","火龍果","哈密瓜","百香果","荔枝","櫻桃","藍莓"});
        f.jdbc.themeOptions.put(7,"{\"A\":{\"text\":\"安靜\",\"score\":{\"pear\":5,\"persimmon\":4,\"banana\":3,\"waxapple\":2,\"dragonfruit\":1}},\"B\":{\"text\":\"活力\",\"score\":{\"melon\":5,\"passionfruit\":4,\"lychee\":3,\"cherry\":2,\"blueberry\":1}}}");
        String[] names={"高接梨","牛心柿","南投山蕉","黑珍珠蓮霧","紅肉火龍果","美濃哈密瓜","黃金百香果","玉荷包荔枝","深紅櫻桃","鮮採藍莓","金鑽鳳梨"};
        for(int i=0;i<names.length;i++)addQuizProduct(f,100+i,names[i],1,20,4.9);
        assertEquals(Arrays.asList(names).subList(0,5),f.service.finish(1,7,f.service.start(1,7),f.answers("A")).recommendations().stream().map(ChatQuizService.Recommendation::productName).toList());
        assertEquals(Arrays.asList(names).subList(5,10),f.service.finish(1,7,f.service.start(1,7),f.answers("B")).recommendations().stream().map(ChatQuizService.Recommendation::productName).toList());
    }
    @Test public void everyQuizResultKeepsItsOwnSnapshotAndMemberHistoryIsPaged(){
        var f=new QuizFixture();for(int i=0;i<23;i++)f.service.finish(1,7,f.service.start(1,7),f.answers(i%2==0?"A":"B"));
        f.service.finish(2,8,f.service.start(2,8),f.answers("A"));f.products.clear();
        var recent=f.service.history(1,null);assertEquals(20,recent.results().size());assertTrue(recent.hasMore());assertEquals(Integer.valueOf(23),recent.results().get(0).answerId());
        assertTrue(recent.results().get(0).result().headline().contains("西瓜"));assertTrue(recent.results().get(1).result().headline().contains("草莓"));assertEquals(5,recent.results().get(0).result().recommendations().size());
        var older=f.service.history(1,recent.results().get(19).answerId());assertEquals(3,older.results().size());assertFalse(older.hasMore());assertEquals(Integer.valueOf(1),older.results().get(2).answerId());
        assertEquals(1,f.service.history(2,null).results().size());assertTrue(f.service.history(3,null).results().isEmpty());
        try{f.service.history(1,0);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void legacyQuizAnswerIsRetainedWithoutInventingHistoricalProducts(){
        var f=new QuizFixture();f.jdbc.history.add(Map.of("answer_id",1,"member_id",1,"quiz_id",7,"quiz_name","舊測驗","score_data","{\"watermelon\":25}","created_at",java.time.LocalDateTime.of(2026,10,17,12,0),"result_name","西瓜","result_desc","早期結果"));
        var result=f.service.history(1,null).results().get(0).result();assertTrue(result.headline().contains("西瓜"));assertTrue(result.description().contains("早期紀錄"));assertTrue(result.recommendations().isEmpty());
    }
    @Test public void quizResultHistoryIsPrivateAndNeverTouchesStaffMessages()throws Exception{
        var f=new QuizFixture();f.service.finish(1,7,f.service.start(1,7),f.answers("A"));var chat=new Fixture();var controller=new FrontChatController(chat.service);field(controller,"quizzes",f.service);var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(401,mvc.perform(get("/api/front/chat/quizzes/results")).andReturn().getResponse().getStatus());var session=new MockHttpSession();session.setAttribute("loggedInMemberId",2);
        var denied=mvc.perform(get("/api/front/chat/quizzes/results").param("memberId","1").session(session)).andReturn().getResponse();assertEquals(200,denied.getStatus());assertTrue(denied.getContentAsString().contains("\"results\":[]"));assertTrue(denied.getHeader("Cache-Control").contains("no-store"));
        session.setAttribute("loggedInMemberId",1);var own=mvc.perform(get("/api/front/chat/quizzes/results").session(session)).andReturn().getResponse();assertEquals(200,own.getStatus());assertTrue(own.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("西瓜"));
        assertTrue(chat.data.isEmpty());assertTrue(chat.destinations.isEmpty());assertEquals(0,chat.cacheTouches);
    }
    @Test public void quizRejectsIncompleteForgedOrForeignAnswersWithoutSaving(){
        var f=new QuizFixture();var run=f.service.start(1,7);
        for(var answers:List.of(Map.of(1,"A"),Map.of(1,"A",2,"A",3,"A",4,"A",999,"A"),f.answers("Z")))try{f.service.finish(1,7,run,answers);fail();}catch(IllegalArgumentException expected){}
        try{f.service.finish(2,7,run,f.answers("A"));fail();}catch(IllegalArgumentException expected){}
        f.jdbc.enabled=false;try{f.service.finish(1,7,run,f.answers("A"));fail();}catch(IllegalArgumentException expected){}assertEquals(0,f.jdbc.inserts);
    }
    @Test public void quizRecommendationsSkipUnavailableFruit(){
        var f=new QuizFixture();f.products.removeIf(p->p.name().equals("西瓜"));var result=f.service.finish(1,7,f.service.start(1,7),f.answers("A"));
        assertEquals(5,result.recommendations().size());assertEquals("芒果",result.recommendations().get(0).fruitName());assertTrue(result.recommendations().stream().noneMatch(p->p.fruitName().equals("西瓜")));
    }
    @Test public void quizApiRequiresMemberAndSessionRunAndRepeatedSubmitOnlySavesOnce()throws Exception {
        var f=new QuizFixture();var controller=new FrontChatController(new Fixture().service);field(controller,"quizzes",f.service);var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(401,mvc.perform(get("/api/front/chat/quizzes")).andReturn().getResponse().getStatus());var session=new MockHttpSession();session.setAttribute("loggedInMemberId",1);
        assertEquals(403,mvc.perform(post("/api/front/chat/quizzes/7/start").session(session)).andReturn().getResponse().getStatus());
        var response=mvc.perform(post("/api/front/chat/quizzes/7/start").session(session).header("X-Requested-With","XMLHttpRequest")).andReturn().getResponse();assertEquals(200,response.getStatus());
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var start=json.readValue(response.getContentAsString(),Map.class);String body=json.writeValueAsString(Map.of("token",start.get("token"),"answers",f.answers("A")));
        for(int i=0;i<2;i++)assertEquals(200,mvc.perform(post("/api/front/chat/quizzes/7/submit").session(session).header("X-Requested-With","XMLHttpRequest").contentType("application/json").content(body)).andReturn().getResponse().getStatus());assertEquals(1,f.jdbc.inserts);
        session.setAttribute("loggedInMemberId",2);assertEquals(400,mvc.perform(post("/api/front/chat/quizzes/7/submit").session(session).header("X-Requested-With","XMLHttpRequest").contentType("application/json").content(body)).andReturn().getResponse().getStatus());assertEquals(1,f.jdbc.inserts);
    }
    @Test public void quizCommandsDoNotCreateChatSourceMessagesUnreadCacheOrStaffPush()throws Exception {
        var f=new Fixture();var mvc=MockMvcBuilders.standaloneSetup(new FrontChatController(f.service)).build();var session=new MockHttpSession();session.setAttribute("loggedInMemberId",1);
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals(401,mvc.perform(post("/api/front/chat/messages").header("X-Requested-With","XMLHttpRequest").param("content","推薦水果")).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/front/chat/messages").session(session).param("content","推薦水果")).andReturn().getResponse().getStatus());
        for(String command:List.of("想請你推薦適合的水果。","推薦水果","幫我推薦水果","水果推薦","心理測驗","水果測驗","小測驗")){
            var response=mvc.perform(post("/api/front/chat/messages").session(session).header("X-Requested-With","XMLHttpRequest").param("content",command).param("productSkuId","11")).andReturn().getResponse();
            assertEquals(200,response.getStatus());assertEquals("FRUIT_QUIZ",json.readValue(response.getContentAsString(),Map.class).get("action"));
        }
        assertTrue(f.data.isEmpty());assertTrue(f.images.isEmpty());assertTrue(f.destinations.isEmpty());assertEquals(0,f.cacheTouches);assertEquals(0,f.commits);
        var ordinary=mvc.perform(post("/api/front/chat/messages").session(session).header("X-Requested-With","XMLHttpRequest").param("content","這好吃嗎").param("productSkuId","11")).andReturn().getResponse();
        assertEquals(200,ordinary.getStatus());assertEquals(2,f.data.size());assertTrue(f.data.get(0).getMessageContent().startsWith("客戶詢問來源"));assertEquals("這好吃嗎",f.data.get(1).getMessageContent());assertTrue(f.destinations.contains(ChatService.STAFF_TOPIC));
    }
    @Test public void fruitRecommendationOpensQuizButDoesNotProduceGenericFaqReply()throws Exception {
        for(String text:List.of("推薦水果","幫我推薦水果","想請你推薦適合的水果。","水果推薦","心理測驗"))assertTrue(ChatQuizService.isQuizRequest(text));
        for(String text:List.of("不要推薦水果","我不想做心理測驗","搜尋柚子","這好吃嗎"))assertFalse(ChatQuizService.isQuizRequest(text));
        var f=new Fixture();field(f.service,"cannedReplyService",new CannedReplyService(){@Override public String faqReply(String text){fail("Quiz intent should not use generic FAQ");return null;}});f.service.sendMemberMessage(1,"推薦水果",null);assertEquals(1,f.data.size());
    }
}
