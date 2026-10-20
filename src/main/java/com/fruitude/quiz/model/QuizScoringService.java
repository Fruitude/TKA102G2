package com.fruitude.quiz.model;

import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Validates configured options and sums hidden server-side weights. No database or catalog access. */
@Service
public class QuizScoringService {
    private final JsonMapper json=JsonMapper.builder().build();
    private static final Map<String,String> FRUITS=Map.ofEntries(
        Map.entry("banana","香蕉"),Map.entry("watermelon","西瓜"),Map.entry("strawberry","草莓"),
        Map.entry("guava","芭樂"),Map.entry("mango","芒果"),Map.entry("pineapple","鳳梨"),
        Map.entry("grape","葡萄"),Map.entry("apple","蘋果"),Map.entry("kiwi","奇異果"),
        Map.entry("pomelo","文旦"),Map.entry("peach","水蜜桃"),Map.entry("orange","柳橙"),
        Map.entry("melon","哈密瓜"),Map.entry("cherry","櫻桃"),Map.entry("blueberry","藍莓"),
        Map.entry("passionfruit","百香果"),Map.entry("lychee","荔枝"),Map.entry("dragonfruit","火龍果"),
        Map.entry("lemon","檸檬"),Map.entry("pear","梨"),Map.entry("persimmon","柿子"),Map.entry("waxapple","蓮霧"));

    public record Option(String text,Map<String,Integer> scores) {}
    public record Question(Integer id,String text,Map<String,Option> options) {}

    public static String fruitName(String key){return FRUITS.get(key);}

    public Map<String,Option> options(String text){
        var raw=json.readValue(text,Map.class);var result=new LinkedHashMap<String,Option>();
        for(Object object:raw.entrySet()){
            var entry=(Map.Entry<?,?>)object;
            if(!(entry.getKey() instanceof String key)||!key.matches("[A-Z]")||!(entry.getValue() instanceof Map<?,?> value)||!(value.get("text") instanceof String label)||label.isBlank()||!(value.get("score") instanceof Map<?,?> weights))throw new IllegalArgumentException("測驗選項設定不完整。");
            var scores=new LinkedHashMap<String,Integer>();
            for(var weight:weights.entrySet()){
                if(!(weight.getKey() instanceof String fruit)||!FRUITS.containsKey(fruit)||!(weight.getValue() instanceof Number number)||number.doubleValue()!=number.intValue()||number.intValue()<0||number.intValue()>5)throw new IllegalArgumentException("測驗配分設定不完整。");
                scores.put(fruit,number.intValue());
            }
            if(scores.isEmpty())throw new IllegalArgumentException("測驗配分設定不完整。");result.put(key,new Option(label,Map.copyOf(scores)));
        }
        if(result.size()<2||result.size()>6)throw new IllegalArgumentException("測驗選項設定不完整。");return result;
    }

    public Map<String,Integer> score(List<Question> questions,Map<Integer,String> answers){
        if(answers==null||answers.size()!=questions.size()||!answers.keySet().equals(questions.stream().map(Question::id).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("請完成所有題目再查看結果。");
        var scores=new TreeMap<String,Integer>();
        for(var question:questions){
            var option=question.options().get(answers.get(question.id()));
            if(option==null)throw new IllegalArgumentException("答案選項不正確，請重新作答。");
            option.scores().forEach((fruit,value)->scores.merge(fruit,value,Integer::sum));
        }
        return scores;
    }
}
