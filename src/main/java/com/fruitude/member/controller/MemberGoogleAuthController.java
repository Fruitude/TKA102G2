package com.fruitude.member.controller;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fruitude.member.model.MemberService;
import com.fruitude.member.model.MemberVO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Google OAuth 2.0 會員登入流程；Google 驗證完成後仍建立既有的會員 Session。 */
@RestController
@RequestMapping("/api/members/google")
public class MemberGoogleAuthController {

	private static final String GOOGLE_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String GOOGLE_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
	private static final String OAUTH_STATE_SESSION_KEY = "memberGoogleOauthState";
	private static final String OAUTH_NEXT_SESSION_KEY = "memberGoogleOauthNext";
	private static final String OAUTH_MODE_SESSION_KEY = "memberGoogleOauthMode";
	private static final String GOOGLE_REGISTRATION_EMAIL_SESSION_KEY = "memberGoogleRegistrationEmail";
	private static final String GOOGLE_REGISTRATION_NAME_SESSION_KEY = "memberGoogleRegistrationName";

	private final MemberService memberService;
	private final RestClient restClient = RestClient.create();
	private final SecureRandom secureRandom = new SecureRandom();
	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;

	public MemberGoogleAuthController(MemberService memberService,
			@Value("${app.google.client-id:}") String clientId,
			@Value("${app.google.client-secret:}") String clientSecret,
			@Value("${app.google.redirect-uri:}") String redirectUri) {
		this.memberService = memberService;
		this.clientId = clientId == null ? "" : clientId.trim();
		this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
		this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
	}

	/** 建立隨機 state 防止 CSRF，接著將瀏覽器導向 Google 帳號選擇頁。 */
	@GetMapping("/authorize")
	public ResponseEntity<Void> authorize(@RequestParam(name = "next", required = false) String next,
			@RequestParam(name = "mode", required = false) String mode,
			HttpServletRequest request) {
		if (!isConfigured()) return redirectToLogin(request, "Google 登入尚未設定");

		byte[] randomBytes = new byte[24];
		secureRandom.nextBytes(randomBytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		HttpSession session = request.getSession(true);
		session.setAttribute(OAUTH_STATE_SESSION_KEY, state);
		session.setAttribute(OAUTH_NEXT_SESSION_KEY, safeNext(next, request.getContextPath()));
		session.setAttribute(OAUTH_MODE_SESSION_KEY, "register".equalsIgnoreCase(mode) ? "register" : "login");

		String location = UriComponentsBuilder.fromUriString(GOOGLE_AUTHORIZE_URL)
				.queryParam("client_id", clientId)
				.queryParam("redirect_uri", redirectUri)
				.queryParam("response_type", "code")
				.queryParam("scope", "openid email profile")
				.queryParam("state", state)
				.queryParam("prompt", "select_account")
				.build().encode().toUriString();
		return redirect(location);
	}

	/** 驗證 state、交換 Access Token、取得已驗證 Email，最後建立本系統會員 Session。 */
	@GetMapping("/callback")
	public ResponseEntity<Void> callback(@RequestParam(name = "code", required = false) String code,
			@RequestParam(name = "state", required = false) String state,
			@RequestParam(name = "error", required = false) String oauthError,
			HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) return redirectToLogin(request, "Google 登入已逾時，請重新操作");
		Object expectedState = session.getAttribute(OAUTH_STATE_SESSION_KEY);
		session.removeAttribute(OAUTH_STATE_SESSION_KEY);
		if (oauthError != null) return redirectToLogin(request, "您已取消 Google 登入");
		if (!(expectedState instanceof String) || state == null || !state.equals(expectedState)) {
			return redirectToLogin(request, "Google 登入驗證失敗，請重新操作");
		}
		if (code == null || code.trim().isEmpty()) {
			return redirectToLogin(request, "Google 沒有回傳登入授權碼");
		}

		try {
			Map<String, Object> tokenResponse = exchangeCode(code);
			String accessToken = value(tokenResponse.get("access_token"));
			if (accessToken.isEmpty()) throw new IllegalArgumentException("Google 沒有回傳登入憑證");
			Map<String, Object> profile = loadGoogleProfile(accessToken);
			if (!Boolean.TRUE.equals(profile.get("email_verified"))) {
				throw new IllegalArgumentException("Google 信箱尚未完成驗證");
			}
			String verifiedEmail = value(profile.get("email"));
			String oauthMode = String.valueOf(session.getAttribute(OAUTH_MODE_SESSION_KEY));
			session.removeAttribute(OAUTH_MODE_SESSION_KEY);
			if ("register".equals(oauthMode) && !memberService.hasMemberWithEmail(verifiedEmail)) {
				session.setAttribute(GOOGLE_REGISTRATION_EMAIL_SESSION_KEY, verifiedEmail);
				session.setAttribute(GOOGLE_REGISTRATION_NAME_SESSION_KEY, value(profile.get("name")));
				return redirect(request.getContextPath() + "/front/about/sign-up/?googleRegister=1");
			}
			if ("register".equals(oauthMode)) {
				return redirectToLogin(request, "此 Google 信箱已註冊會員，請直接登入");
			}
			MemberVO member = memberService.loginWithVerifiedEmail(verifiedEmail);
			request.changeSessionId();
			session.setAttribute("loggedInMemberId", member.getMemberId());
			session.setAttribute("loggedInMemberName", member.getMemberName());
			String next = safeNext((String) session.getAttribute(OAUTH_NEXT_SESSION_KEY), request.getContextPath());
			session.removeAttribute(OAUTH_NEXT_SESSION_KEY);
			return redirect(next == null ? request.getContextPath() + "/front/" : next);
		} catch (IllegalArgumentException error) {
			return redirectToLogin(request, error.getMessage());
		} catch (RestClientException error) {
			return redirectToLogin(request, "目前無法連線至 Google，請稍後再試");
		}
	}

	/** 回傳剛完成 Google 驗證的註冊資料，讓註冊頁預填姓名與 Email。 */
	@GetMapping("/registration-profile")
	public ResponseEntity<Map<String, String>> registrationProfile(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		Map<String, String> body = new LinkedHashMap<>();
		if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
		String email = value(session.getAttribute(GOOGLE_REGISTRATION_EMAIL_SESSION_KEY));
		if (email.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
		body.put("email", email);
		body.put("name", value(session.getAttribute(GOOGLE_REGISTRATION_NAME_SESSION_KEY)));
		return ResponseEntity.ok(body);
	}

	private Map<String, Object> exchangeCode(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("code", code);
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("redirect_uri", redirectUri);
		form.add("grant_type", "authorization_code");
		return restClient.post().uri(GOOGLE_TOKEN_URL)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {});
	}

	private Map<String, Object> loadGoogleProfile(String accessToken) {
		return restClient.get().uri(GOOGLE_USERINFO_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {});
	}

	private boolean isConfigured() {
		return !clientId.isEmpty() && !clientSecret.isEmpty() && !redirectUri.isEmpty();
	}

	private String safeNext(String next, String contextPath) {
		if (next == null || !next.startsWith(contextPath + "/front/")
				|| next.contains("//") || next.contains("\\")) return null;
		return next;
	}

	private String value(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	private ResponseEntity<Void> redirectToLogin(HttpServletRequest request, String message) {
		String location = UriComponentsBuilder.fromPath(request.getContextPath() + "/front/about/login/")
				.queryParam("authError", message).build().encode().toUriString();
		return redirect(location);
	}

	private ResponseEntity<Void> redirect(String location) {
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
	}
}
