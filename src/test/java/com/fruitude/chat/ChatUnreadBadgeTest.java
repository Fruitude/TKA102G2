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
}
