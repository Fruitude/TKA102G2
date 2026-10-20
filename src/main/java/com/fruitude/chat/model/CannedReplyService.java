package com.fruitude.chat.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 罐頭訊息的建議回覆：用關鍵字比對會員的訊息（不用 AI）
// 每筆罐頭訊息的 keyword 以逗號拆開，會員訊息裡出現幾個就得幾分；分數高的排前面，同分依罐頭訊息的顯示順序
// 會員端 FAQ 自動回覆委派給 FaqService，客服罐頭建議維持原有規則
@Service
public class CannedReplyService {
    @Autowired private com.fruitude.faq.model.FaqService faqService;

    // Keep the existing chat entry point; FAQ rules and persistence live in the faq module.
    public String faqReply(String text){return faqService.reply(text);}

	// 至少命中幾個關鍵字才列入建議
	private static final int MIN_MATCHED_KEYWORDS = 1;

	// 最多回傳幾筆建議
	private static final int MAX_SUGGESTIONS = 3;

	@Autowired
	private CannedMessageRepository cannedMessageRepository;

	@Autowired
	private MessageRepository messageRepository;

	// 比對時用的一筆結果：罐頭訊息、命中的關鍵字
	private record Match(CannedMessage cannedMessage, List<String> matchedKeywords) {
	}

	// 一位會員的建議回覆：拿會員最新一則有文字的訊息去比對；會員沒有送過訊息時回傳空的清單
	public List<CannedSuggestionView> getSuggestions(Integer memberId) {
		return messageRepository
				.findFirstByMemberIdAndSenderTypeAndStatusAndMessageContentIsNotNullOrderByCreatedAtDescMessageIdDesc(
						memberId, Message.SENDER_MEMBER, Message.STATUS_SHOWN)
				.map(message -> suggest(message.getMessageContent()))
				.orElse(List.of());
	}

	// 用一段文字比對所有可用的罐頭訊息，回傳最多 MAX_SUGGESTIONS 筆建議
	// 中文直接比對字串是否包含關鍵字，不需要斷詞；英文不分大小寫
	public List<CannedSuggestionView> suggest(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String normalizedText = text.toLowerCase(Locale.ROOT);

		// findAvailable 已經依顯示順序排好，排序是穩定的，所以同分時會保持顯示順序
		List<Match> matches = new ArrayList<>();
		for (CannedMessage cannedMessage : cannedMessageRepository.findAvailable()) {
			List<String> matchedKeywords = new ArrayList<>();
			for (String keyword : splitKeywords(cannedMessage.getKeyword())) {
				if (normalizedText.contains(keyword.toLowerCase(Locale.ROOT)) && !matchedKeywords.contains(keyword)) {
					matchedKeywords.add(keyword);
				}
			}
			if (matchedKeywords.size() >= MIN_MATCHED_KEYWORDS) {
				matches.add(new Match(cannedMessage, matchedKeywords));
			}
		}
		matches.sort(Comparator.comparingInt((Match match) -> match.matchedKeywords().size()).reversed());

		List<CannedSuggestionView> suggestions = new ArrayList<>();
		for (Match match : matches.subList(0, Math.min(MAX_SUGGESTIONS, matches.size()))) {
			CannedMessage cannedMessage = match.cannedMessage();
			suggestions.add(new CannedSuggestionView(cannedMessage.getCannedId(), cannedMessage.getTitle(),
					cannedMessage.getMessageContent(), match.matchedKeywords()));
		}
		return suggestions;
	}

	// 這個罐頭訊息存不存在；送出時用來確認頁面帶來的 cannedId
	public boolean exists(Integer cannedId) {
		return cannedId != null && cannedMessageRepository.existsById(cannedId);
	}

	// 逗號分隔的關鍵字拆成清單：全形逗號、頓號也當作分隔，去掉前後空白與空字串
	private List<String> splitKeywords(String keyword) {
		List<String> keywords = new ArrayList<>();
		if (keyword == null) {
			return keywords;
		}
		for (String part : keyword.split("[,，、]")) {
			String trimmed = part.strip();
			if (!trimmed.isEmpty()) {
				keywords.add(trimmed);
			}
		}
		return keywords;
	}

}
