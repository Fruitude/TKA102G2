package com.fruitude.member.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * 追蹤目前有 session 的登入會員。登入時 session 會放入 loggedInMemberId，
 * 登出或 session 逾時失效時移除，因此人數就是「目前登入中的會員」。
 * 只存在這台伺服器的記憶體裡，伺服器重啟後歸零。
 */
@Component
public class OnlineMemberTracker implements HttpSessionAttributeListener, HttpSessionListener {

	private static final String ATTRIBUTE = "loggedInMemberId";

	// sessionId -> memberId
	private final Map<String, Integer> sessions = new ConcurrentHashMap<>();

	/** 目前登入的會員人數；同一位會員多處登入只算一位。 */
	public int countOnlineMembers() {
		Set<Integer> memberIds = new HashSet<>(sessions.values());
		return memberIds.size();
	}

	@Override
	public void attributeAdded(HttpSessionBindingEvent event) {
		track(event);
	}

	@Override
	public void attributeReplaced(HttpSessionBindingEvent event) {
		track(event);
	}

	@Override
	public void attributeRemoved(HttpSessionBindingEvent event) {
		if (ATTRIBUTE.equals(event.getName())) {
			sessions.remove(event.getSession().getId());
		}
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		sessions.remove(event.getSession().getId());
	}

	private void track(HttpSessionBindingEvent event) {
		if (ATTRIBUTE.equals(event.getName())) {
			Object value = event.getSession().getAttribute(ATTRIBUTE);
			if (value instanceof Integer) {
				sessions.put(event.getSession().getId(), (Integer) value);
			}
		}
	}
}
