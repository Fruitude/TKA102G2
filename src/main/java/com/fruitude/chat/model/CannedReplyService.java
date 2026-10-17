package com.fruitude.chat.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 罐頭訊息的建議回覆：用關鍵字比對會員的訊息（不用 AI）
// 每筆罐頭訊息的 keyword 以逗號拆開，會員訊息裡出現幾個就得幾分；分數高的排前面，同分依罐頭訊息的顯示順序
// 之後做會員端的自動回覆時，比對規則沿用 suggest
@Service
public class CannedReplyService {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate faqJdbc;
    @Autowired private com.fruitude.promo.model.PromoRepository faqPromos;
    private record FaqRow(String question,String answer,String keyword) {}

    // FAQ keywords: commas separate alternatives; & means all words in that alternative must match.
    // Only enabled categories with an enabled ancestor path can answer. More specific matches win.
    public String faqReply(String text) {
        if(text==null || text.isBlank())return null;
        String normalized=normalizeFaq(text);
        var rows=faqJdbc.query("WITH RECURSIVE active_categories AS ("
            +"SELECT faq_category_id FROM faq_category WHERE parent_category_id IS NULL AND status=1 "
            +"UNION ALL SELECT c.faq_category_id FROM faq_category c JOIN active_categories p "
            +"ON c.parent_category_id=p.faq_category_id WHERE c.status=1) "
            +"SELECT f.question,f.answer,f.keyword FROM faq f JOIN active_categories c "
            +"ON c.faq_category_id=f.faq_category_id ORDER BY f.sort_order,f.faq_id",
            (rs,n)->new FaqRow(rs.getString("question"),rs.getString("answer"),rs.getString("keyword")));
        FaqRow best=null;int bestScore=0;
        for(var row:rows){int longest=0,count=0;
            for(String alternative:splitKeywords(row.keyword())){
                int length=0;boolean matches=true;
                for(String part:alternative.split("&",-1)){
                    String term=normalizeFaq(part);if(term.isEmpty()||!normalized.contains(term)){matches=false;break;}length+=term.length();
                }
                if(matches){longest=Math.max(longest,length);count++;}
            }
            int score=longest*100+Math.min(count,99);
            if(score>bestScore && row.answer()!=null && !row.answer().isBlank()){best=row;bestScore=score;}
        }
        if(best==null)return null;
        String answer=liveFaqAnswer(best.question(),best.answer());
        return answer.length()>1000?answer.substring(0,997)+"…":answer;
    }
    private static String normalizeFaq(String text){return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+","");}
    private String liveFaqAnswer(String question,String fallback){
        String type=switch(question){case "最近有哪些活動？" -> "ALL";case "生日優惠是幾折？" -> "BIRTHDAY_MONTH";
            case "新會員有優惠嗎？" -> "NEW_MEMBER_FIRST_ORDER";case "運費多少？多少免運？" -> "FREE_SHIPPING";default -> null;};
        if(type==null)return fallback;
        var now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
        var promos=type.equals("ALL")?faqPromos.findActive(now):faqPromos.findActiveByType(type,now);
        if(promos.isEmpty())return type.equals("ALL")?"目前沒有進行中的優惠活動，歡迎留意商城的限時優惠頁面。":"目前沒有進行中的這類優惠，實際金額請以結帳畫面為準。";
        var lines=new ArrayList<String>();
        for(var promo:promos){
            String benefit=com.fruitude.promo.model.BenefitType.describe(promo.getBenefitType(),promo.getBenefitValue());
            String line=promo.getPromoProjectTitle()+"："+benefit;
            if(promo.getMinOrderAmount()!=null && promo.getMinOrderAmount()>0)line+="，訂單滿 "+promo.getMinOrderAmount()+" 元適用";
            if("BIRTHDAY_MONTH".equals(promo.getPromoType()))line+="，生日當月每年限用一次，結帳時需勾選使用";
            if("NEW_MEMBER_FIRST_ORDER".equals(promo.getPromoType()))line+="，首次下單適用，限用一次";
            if(promo.getPromoProjectEnd()!=null && promo.getPromoProjectEnd().getYear()<2100)line+="，至 "+promo.getPromoProjectEnd().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
            lines.add(line);
        }
        return String.join("\n",lines)+"。實際資格、運費與優惠金額，以結帳畫面為準。";
    }


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
