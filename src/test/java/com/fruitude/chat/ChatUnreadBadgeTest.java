package com.fruitude.chat;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.time.*;
import com.fruitude.chat.model.*;
import com.fruitude.chat.controller.AdminChatController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.dao.DataAccessResourceFailureException;

public class ChatUnreadBadgeTest {
    @Test public void employeeNamesAreOnlyInStaffHistoryAndStaffEvents()throws Exception {
        var fixture=new FrontChatTest.Fixture();var payloads=new ArrayList<Object>();
        FrontChatTest.field(fixture.service,"messagingTemplate",new org.springframework.messaging.simp.SimpMessagingTemplate((message,timeout)->{payloads.add(message.getPayload());return true;}));
        fixture.service.sendStaffMessage(1,9,"您好",null,null);
        assertTrue(payloads.get(0) instanceof ChatService.StaffEvent);
        var staff=(ChatService.StaffEvent)payloads.get(0);assertEquals("測試客服小林",staff.message().employeeName());assertEquals(Integer.valueOf(9),staff.message().employeeId());
        assertTrue(payloads.get(1) instanceof ChatEvent);
        assertEquals("測試客服小林",fixture.service.getStaffConversation(1).get(0).employeeName());
        var mvc=MockMvcBuilders.standaloneSetup(new com.fruitude.chat.controller.FrontChatController(fixture.service)).build();
        var session=new org.springframework.mock.web.MockHttpSession();session.setAttribute("loggedInMemberId",1);
        String body=mvc.perform(get("/api/front/chat/messages").session(session)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("employeeId"));assertFalse(body.contains("employeeName"));assertFalse(body.contains("測試客服小林"));
    }
    @Test public void totalSumsAllMemberConversationsFromRedisWithoutDatabase()throws Exception {
        var fixture=new FrontChatTest.Fixture();
        FrontChatTest.field(fixture.service,"chatRedisService",new ChatRedisService(){
            @Override public List<ConversationEntry> readConversations(){return List.of(new ConversationEntry(1,LocalDateTime.now(),3),new ConversationEntry(2,LocalDateTime.now(),7));}
        });
        assertEquals(10,fixture.service.staffUnreadCount());
    }
    @Test public void noUnreadReturnsZeroAndRedisOutageFallsBack()throws Exception {
        var fixture=new FrontChatTest.Fixture();
        FrontChatTest.field(fixture.service,"chatRedisService",new ChatRedisService(){
            @Override public List<ConversationEntry> readConversations(){return List.of();}
        });assertEquals(0,fixture.service.staffUnreadCount());
        FrontChatTest.field(fixture.service,"chatRedisService",new ChatRedisService(){
            @Override public List<ConversationEntry> readConversations(){throw new DataAccessResourceFailureException("test outage");}
        });assertEquals(0,fixture.service.staffUnreadCount());
    }
    @Test public void countApiRequiresChatPermissionAndDoesNotCache()throws Exception {
        var controller=new AdminChatController();var fixture=new FrontChatTest.Fixture();
        FrontChatTest.field(fixture.service,"chatRedisService",new ChatRedisService(){
            @Override public List<ConversationEntry> readConversations(){return List.of(new ConversationEntry(1,LocalDateTime.now(),12));}
        });FrontChatTest.field(controller,"chatSvc",fixture.service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(403,mvc.perform(get("/api/admin/chat/unread")).andReturn().getResponse().getStatus());
        var response=mvc.perform(get("/api/admin/chat/unread").requestAttr("adminPermissionCodes",Set.of("CHAT_VIEW"))).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("12"));assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    @Test public void previewRequiresChatPermissionAndDoesNotMarkRead()throws Exception {
        var controller=new AdminChatController();var fixture=new FrontChatTest.Fixture();FrontChatTest.field(controller,"chatSvc",fixture.service);
        var first=fixture.add(1,1,1);var second=fixture.add(1,1,1);
        var alreadyRead=fixture.add(1,1,1);alreadyRead.setIsRead(1);
        fixture.add(1,2,1);fixture.add(1,1,0);fixture.add(2,1,1);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(403,mvc.perform(get("/api/admin/chat/1/preview")).andReturn().getResponse().getStatus());
        var response=mvc.perform(get("/api/admin/chat/1/preview").requestAttr("adminPermissionCodes",Set.of("CHAT_VIEW"))).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
        var preview=fixture.service.getUnreadPreview(1);
        assertEquals(List.of(first.getMessageId(),second.getMessageId()),preview.stream().map(ChatMessageView::messageId).toList());
        assertEquals(Integer.valueOf(0),first.getIsRead());assertEquals(Integer.valueOf(0),second.getIsRead());
        assertTrue(fixture.events.isEmpty());
    }
    private static Message read(Message message){message.setIsRead(1);message.setReadAt(LocalDateTime.now());return message;}
    @Test public void markUnreadOnlyRevertsMemberMessagesAfterLastStaffReply()throws Exception {
        var fixture=new FrontChatTest.Fixture();
        var answered=read(fixture.add(1,1,1));read(fixture.add(1,2,1));
        var first=read(fixture.add(1,1,1));var second=read(fixture.add(1,1,1));var hidden=read(fixture.add(1,1,0));var other=read(fixture.add(2,1,1));
        fixture.service.markUnreadByStaff(1);
        assertEquals(Integer.valueOf(1),answered.getIsRead());
        assertEquals(Integer.valueOf(0),first.getIsRead());assertNull(first.getReadAt());assertEquals(Integer.valueOf(0),second.getIsRead());
        assertEquals(Integer.valueOf(1),hidden.getIsRead());assertEquals(Integer.valueOf(1),other.getIsRead());
        assertEquals(1,fixture.events.size());assertEquals(ChatEvent.UNREAD,fixture.events.get(0).type());
        assertEquals(List.of(ChatService.STAFF_TOPIC),fixture.destinations);
    }
    @Test public void markUnreadRejectedWhenLastMessageIsStaff()throws Exception {
        var fixture=new FrontChatTest.Fixture();
        var member=read(fixture.add(1,1,1));read(fixture.add(1,2,1));
        try{fixture.service.markUnreadByStaff(1);fail();}catch(IllegalArgumentException expected){assertEquals("最後一則是客服訊息，不能標為未讀",expected.getMessage());}
        assertEquals(Integer.valueOf(1),member.getIsRead());assertTrue(fixture.events.isEmpty());
    }
    @Test public void markUnreadRejectedWhenSystemLastWithoutPendingMember()throws Exception {
        var fixture=new FrontChatTest.Fixture();
        var member=read(fixture.add(1,1,1));read(fixture.add(1,2,1));read(fixture.add(1,0,1));
        try{fixture.service.markUnreadByStaff(1);fail();}catch(IllegalArgumentException expected){assertEquals("沒有可以標為未讀的會員訊息",expected.getMessage());}
        assertEquals(Integer.valueOf(1),member.getIsRead());assertTrue(fixture.events.isEmpty());
        try{fixture.service.markUnreadByStaff(3);fail();}catch(IllegalArgumentException expected){assertEquals("這個對話沒有訊息",expected.getMessage());}
    }
    @Test public void markUnreadApiRequiresChatPermission()throws Exception {
        var controller=new AdminChatController();var fixture=new FrontChatTest.Fixture();FrontChatTest.field(controller,"chatSvc",fixture.service);
        var member=read(fixture.add(1,1,1));
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(403,mvc.perform(post("/api/admin/chat/1/unread")).andReturn().getResponse().getStatus());
        assertEquals(Integer.valueOf(1),member.getIsRead());
        assertEquals(200,mvc.perform(post("/api/admin/chat/1/unread").requestAttr("adminPermissionCodes",Set.of("CHAT_VIEW"))).andReturn().getResponse().getStatus());
        assertEquals(Integer.valueOf(0),member.getIsRead());
        read(fixture.add(1,2,1));
        var rejected=mvc.perform(post("/api/admin/chat/1/unread").requestAttr("adminPermissionCodes",Set.of("CHAT_VIEW"))).andReturn().getResponse();
        assertEquals(400,rejected.getStatus());assertTrue(rejected.getContentAsString().contains("最後一則是客服訊息"));
    }
}
