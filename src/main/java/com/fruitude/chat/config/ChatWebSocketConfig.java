package com.fruitude.chat.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.fruitude.chat.model.ChatService;

// 前後台聊天室的 WebSocket（STOMP）設定
// 頁面連到 /admin/chat/ws，連線前由 ChatHandshakeInterceptor 檢查員工登入與 CHAT_VIEW 權限
// WebSocket 只用來推播（伺服器 → 頁面）；送訊息一律走 AdminChatController 的 API，才會經過權限檢查並存進資料庫
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final java.util.concurrent.ConcurrentMap<String,java.util.Map<String,Object>> sessions=new java.util.concurrent.ConcurrentHashMap<>();

	@Autowired
	private ChatHandshakeInterceptor chatHandshakeInterceptor;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/admin/chat/ws").addInterceptors(chatHandshakeInterceptor);
        registry.addEndpoint("/front/chat/ws").addInterceptors(chatHandshakeInterceptor);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// 伺服器內建的簡易 broker，推播目的地都是 /topic 開頭
		registry.enableSimpleBroker("/topic");
	}

	// 員工只能訂閱後台頻道，會員只能訂閱自己的頻道；所有用戶端都禁止 SEND。
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				if (accessor == null) {
					return message;
				}
                if(accessor.getCommand()==StompCommand.CONNECT && ChatHandshakeInterceptor.active(accessor.getSessionAttributes()))
                    sessions.put(accessor.getSessionId(),accessor.getSessionAttributes());
                if(accessor.getCommand()==StompCommand.DISCONNECT)sessions.remove(accessor.getSessionId());
                if(accessor.getCommand()!=null && accessor.getCommand()!=StompCommand.DISCONNECT
                    && !ChatHandshakeInterceptor.active(accessor.getSessionAttributes()))throw new MessagingException("登入已失效");
				if (StompCommand.SEND.equals(accessor.getCommand())) {
					throw new MessagingException("聊天室不接受由 WebSocket 送出的訊息");
				}
                if(StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    var attributes=accessor.getSessionAttributes();
                    Object member=attributes.get("memberId");
                    String allowed=member instanceof Integer?ChatService.memberTopic((Integer)member):ChatService.STAFF_TOPIC;
                    if(!allowed.equals(accessor.getDestination()))throw new MessagingException("不能訂閱這個頻道");
                }
				return message;
			}
		});
	}

    /** Observe member login changes without changing the shared member/employee session. */
    @org.springframework.context.annotation.Bean
    public jakarta.servlet.http.HttpSessionAttributeListener chatLoginListener(){
        return new jakarta.servlet.http.HttpSessionAttributeListener(){
            private void changed(jakarta.servlet.http.HttpSessionBindingEvent event,boolean removed){
                if(!"loggedInMemberId".equals(event.getName()))return;
                if(removed)event.getSession().removeAttribute("chat.welcome.token");
                else event.getSession().setAttribute("chat.welcome.token",java.util.UUID.randomUUID().toString());
            }
            @Override public void attributeAdded(jakarta.servlet.http.HttpSessionBindingEvent event){changed(event,false);}
            @Override public void attributeReplaced(jakarta.servlet.http.HttpSessionBindingEvent event){changed(event,false);}
            @Override public void attributeRemoved(jakarta.servlet.http.HttpSessionBindingEvent event){changed(event,true);}
        };
    }

    @org.springframework.context.event.EventListener
    public void disconnected(org.springframework.web.socket.messaging.SessionDisconnectEvent event){sessions.remove(event.getSessionId());}
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor(){
            @Override public Message<?> preSend(Message<?> message,MessageChannel channel){
                var headers=message.getHeaders();
                if(org.springframework.messaging.simp.SimpMessageHeaderAccessor.getMessageType(headers)==org.springframework.messaging.simp.SimpMessageType.MESSAGE) {
                    String sessionId=org.springframework.messaging.simp.SimpMessageHeaderAccessor.getSessionId(headers);
                    if(sessionId==null || !ChatHandshakeInterceptor.active(sessions.get(sessionId)))return null;
                }
                return message;
            }
        });
    }
}
