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

// 後台聊天室的 WebSocket（STOMP）設定
// 頁面連到 /admin/chat/ws，連線前由 ChatHandshakeInterceptor 檢查員工登入與 CHAT_VIEW 權限
// WebSocket 只用來推播（伺服器 → 頁面）；送訊息一律走 AdminChatController 的 API，才會經過權限檢查並存進資料庫
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Autowired
	private ChatHandshakeInterceptor chatHandshakeInterceptor;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/admin/chat/ws").addInterceptors(chatHandshakeInterceptor);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// 伺服器內建的簡易 broker，推播目的地都是 /topic 開頭
		registry.enableSimpleBroker("/topic");
	}

	// 頁面送進來的 STOMP 指令：只能訂閱 ChatService.STAFF_TOPIC；不能用 SEND 直接往 /topic 送（否則可以假造訊息推給其他員工）
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				if (accessor == null) {
					return message;
				}
				if (StompCommand.SEND.equals(accessor.getCommand())) {
					throw new MessagingException("聊天室不接受由 WebSocket 送出的訊息");
				}
				if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
						&& !ChatService.STAFF_TOPIC.equals(accessor.getDestination())) {
					throw new MessagingException("不能訂閱這個頻道");
				}
				return message;
			}
		});
	}

}
