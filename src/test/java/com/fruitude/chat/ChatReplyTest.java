package com.fruitude.chat;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import com.fruitude.chat.model.*;
import com.fruitude.chat.controller.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

public class ChatReplyTest {
    @Test public void memberReplyIsSavedAndSurvivesHistoryReload()throws Exception {
        var fixture=new FrontChatTest.Fixture();var original=fixture.add(1,2,1);original.setMessageContent("請問希望何時收到？");
        var sent=fixture.service.sendMemberMessage(1,"下週一，謝謝",null,original.getMessageId());
        assertEquals(original.getMessageId(),fixture.data.get(1).getReplyMessageId());assertEquals(original.getMessageId(),sent.replyMessageId());
        assertEquals("請問希望何時收到？",sent.reply().messageContent());assertEquals(Integer.valueOf(2),sent.reply().senderType());
        var reloaded=fixture.service.getMemberConversation(1,null,null).messages().get(1);
        assertEquals(sent.reply(),reloaded.reply());
    }
    @Test public void staffCanReplyToMemberAndOtherStaffMessages()throws Exception {
        var fixture=new FrontChatTest.Fixture();var original=fixture.add(1,1,1);
        var reply=fixture.service.sendStaffMessage(1,9,"好的",null,null,original.getMessageId());
        var nested=fixture.service.sendStaffMessage(1,9,"再補充一下",null,null,reply.messageId());
        assertEquals(Integer.valueOf(1),reply.reply().senderType());assertEquals(reply.messageId(),nested.replyMessageId());
        assertEquals("好的",fixture.service.getStaffConversation(1).get(2).reply().messageContent());
    }
    @Test public void missingForeignAndHiddenReplyTargetsCannotBeWritten()throws Exception {
        var fixture=new FrontChatTest.Fixture();fixture.add(2,2,1);fixture.add(1,2,0);
        for(int target:new int[]{0,-1,1,2,999}){
            int count=fixture.data.size();try{fixture.service.sendMemberMessage(1,"回覆",null,target);fail();}catch(IllegalArgumentException expected){}
            assertEquals(count,fixture.data.size());assertTrue(fixture.events.isEmpty());
        }
    }
    @Test public void imageOnlyPreviewAndHiddenParentDoNotLeakContent()throws Exception {
        var fixture=new FrontChatTest.Fixture();var original=fixture.add(1,2,1);original.setMessageContent(null);original.setMessageType(1);
        var reply=fixture.service.sendMemberMessage(1,"收到圖片",null,original.getMessageId());assertEquals("[圖片訊息]",reply.reply().messageContent());
        original.setStatus(0);var history=fixture.service.getMemberConversation(1,null,null).messages();assertEquals(1,history.size());assertEquals("原訊息已無法顯示",history.get(0).reply().messageContent());
    }
    @Test public void memberReadEventHasVisibleWatermarkAndApiStoresReplyParameter()throws Exception {
        var fixture=new FrontChatTest.Fixture();fixture.add(1,2,1);fixture.add(1,2,1);fixture.service.markReadByMember(1,1);
        assertEquals(Integer.valueOf(1),fixture.events.get(0).readThroughId());assertEquals(Integer.valueOf(0),fixture.data.get(1).getIsRead());
        var mvc=MockMvcBuilders.standaloneSetup(new FrontChatController(fixture.service)).build();
        var session=new org.springframework.mock.web.MockHttpSession();session.setAttribute("loggedInMemberId",1);
        var response=mvc.perform(post("/api/front/chat/messages").session(session).header("X-Requested-With","XMLHttpRequest").param("content","指定回覆").param("replyMessageId","1")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertEquals(Integer.valueOf(1),fixture.data.get(2).getReplyMessageId());
        assertFalse(response.getContentAsString().contains("employeeId"));
    }
}
