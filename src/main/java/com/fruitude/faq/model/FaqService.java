package com.fruitude.faq.model;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fruitude.promo.model.PromoRepository;

/** Matches member FAQ keywords and resolves current promotional wording. */
@Service
public class FaqService {
    private final FaqRepository faqs;
    private final PromoRepository faqPromos;
    public FaqService(FaqRepository faqs,PromoRepository faqPromos){this.faqs=faqs;this.faqPromos=faqPromos;}

    // FAQ keywords: commas separate alternatives; & means all words in that alternative must match.
    // Only enabled categories with an enabled ancestor path can answer. More specific matches win.
    @Transactional(readOnly=true)
    public String reply(String text) {
        if(text==null || text.isBlank())return null;
        String normalized=normalizeFaq(text);
        var rows=faqs.findAvailableForReply();
        Faq best=null;int bestScore=0;
        for(var row:rows){int longest=0,count=0;
            for(String alternative:splitKeywords(row.getKeyword())){
                int length=0;boolean matches=true;
                for(String part:alternative.split("&",-1)){
                    String term=normalizeFaq(part);if(term.isEmpty()||!normalized.contains(term)){matches=false;break;}length+=term.length();
                }
                if(matches){longest=Math.max(longest,length);count++;}
            }
            int score=longest*100+Math.min(count,99);
            if(score>bestScore && row.getAnswer()!=null && !row.getAnswer().isBlank()){best=row;bestScore=score;}
        }
        if(best==null)return null;
        String answer=liveFaqAnswer(best.getQuestion(),best.getAnswer());
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
