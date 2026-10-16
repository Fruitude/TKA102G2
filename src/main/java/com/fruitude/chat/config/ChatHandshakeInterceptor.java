package com.fruitude.chat.config;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.fruitude.employee.model.EmployeeAdminAccessService;
import com.fruitude.employee.model.EmployeeAdminAccessService.AccessProfile;

import jakarta.servlet.http.HttpSession;

// 後台聊天室 WebSocket 連線前的檢查：必須是登入中、而且有 CHAT_VIEW 權限的員工
// WebSocket 連線不會經過 AdminAccessInterceptor，所以在這裡自己擋；員工與權限的判斷沿用 EmployeeAdminAccessService
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

	// 連線成功後放在 WebSocket session 的屬性名稱：員工編號
	public static final String EMPLOYEE_ID_ATTRIBUTE = "employeeId";

	@Autowired
	private EmployeeAdminAccessService accessService;

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
        if(!(request instanceof ServletServerHttpRequest servlet))return false;
        HttpSession session=servlet.getServletRequest().getSession(false);
        if(request.getURI().getPath().endsWith("/front/chat/ws")) {
            Object id=session==null?null:session.getAttribute("loggedInMemberId");
            if(!(id instanceof Number)){response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;}
            attributes.put("memberId",((Number)id).intValue());
            attributes.put("chatSession",session);
            return true;
        }
		Integer employeeId = sessionEmployeeId(request);
		Optional<AccessProfile> profileResult = accessService.findAccessProfile(employeeId);
		if (profileResult.isEmpty() || !profileResult.get().getPermissionCodes().contains("CHAT_VIEW")) {
			response.setStatusCode(HttpStatus.FORBIDDEN);
			return false;
		}
		attributes.put(EMPLOYEE_ID_ATTRIBUTE, employeeId);
        attributes.put("chatSession",session);
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
	}

    public static boolean active(Map<String,Object> attributes) {
        if(attributes==null || !(attributes.get("chatSession") instanceof HttpSession session))return false;
        try {
            String key=attributes.containsKey("memberId")?"memberId":EMPLOYEE_ID_ATTRIBUTE;
            String login=key.equals("memberId")?"loggedInMemberId":"loggedInEmployeeId";
            Object value=session.getAttribute(login);
            return value instanceof Number && java.util.Objects.equals(attributes.get(key),((Number)value).intValue());
        }catch(IllegalStateException expired){return false;}
    }

	// 連線請求帶著的 HttpSession 裡的登入員工（EmployeeSessionController 登入時放的 loggedInEmployeeId）；沒登入時是 null
	private Integer sessionEmployeeId(ServerHttpRequest request) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return null;
		}
		HttpSession session = servletRequest.getServletRequest().getSession(false);
		Object employeeId = session == null ? null : session.getAttribute("loggedInEmployeeId");
		return employeeId instanceof Number ? ((Number) employeeId).intValue() : null;
	}

}
