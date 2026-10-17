package com.fruitude.chat.model;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fruitude.product.model.FrontCatalogService;
import tools.jackson.databind.json.JsonMapper;

/** Five-question fruit quiz. Scores stay on the server; the existing quiz tables store configuration and answers. */
@Service
public class ChatQuizService {
    private final JdbcTemplate jdbc;
    private final FrontCatalogService catalog;
    private final JsonMapper json=JsonMapper.builder().build();
    private static final Map<String,String> FRUITS=Map.ofEntries(
        Map.entry("banana","香蕉"),Map.entry("watermelon","西瓜"),Map.entry("strawberry","草莓"),
        Map.entry("guava","芭樂"),Map.entry("mango","芒果"),Map.entry("pineapple","鳳梨"),
        Map.entry("grape","葡萄"),Map.entry("apple","蘋果"),Map.entry("kiwi","奇異果"),
        Map.entry("pomelo","文旦"),Map.entry("peach","水蜜桃"),Map.entry("orange","柳橙"),
        Map.entry("melon","哈密瓜"),Map.entry("cherry","櫻桃"),Map.entry("blueberry","藍莓"),
        Map.entry("passionfruit","百香果"),Map.entry("lychee","荔枝"),Map.entry("dragonfruit","火龍果"),
        Map.entry("lemon","檸檬"),Map.entry("pear","梨"),Map.entry("persimmon","柿子"),Map.entry("waxapple","蓮霧"));
    public ChatQuizService(JdbcTemplate jdbc,FrontCatalogService catalog){this.jdbc=jdbc;this.catalog=catalog;}
    public record Theme(Integer quizId,String name,String description,int questionCount) {}
    public record Choice(String key,String text) {}
    public record QuestionView(Integer questionId,String text,List<Choice> choices) {}
    public record StartView(String token,Integer quizId,String name,String description,List<QuestionView> questions) {}
    public record Submission(String token,Map<Integer,String> answers) {}
    public record Recommendation(String fruitName,String description,Integer productId,String productName,Integer price) {}
    public record Result(String headline,String description,List<Recommendation> recommendations) {}
    public record SavedResult(Integer answerId,Integer quizId,String theme,LocalDateTime createdAt,Result result) {}
    public record ResultPage(List<SavedResult> results,boolean hasMore) {}
    /** Member-only history reuses quiz answers; it never creates a customer-service message. */
    @Transactional(readOnly=true)
    public ResultPage history(Integer memberId,Integer beforeId){
        if(beforeId!=null&&beforeId<1)throw new IllegalArgumentException("測驗紀錄編號不正確。");
        var rows=jdbc.query("SELECT a.answer_id,a.quiz_id,q.quiz_name,a.created_at,a.score_data,r.result_name,r.result_desc FROM chat_quiz_answer a "
            +"JOIN quiz q ON q.quiz_id=a.quiz_id LEFT JOIN quiz_result r ON r.result_id=a.result_id "
            +"WHERE a.member_id=? AND a.answer_id<? ORDER BY a.answer_id DESC LIMIT 21",(r,n)->{
                var raw=json.readValue(r.getString("score_data"),Map.class);Object snapshot=raw.get("_result");
                var result=snapshot instanceof Map<?,?>?json.readValue(json.writeValueAsString(snapshot),Result.class):
                    new Result("你的果物測驗結果："+Objects.toString(r.getString("result_name"),"果物靈感"),
                        Objects.toString(r.getString("result_desc"),"")+"（此筆為早期紀錄，未保存當時的推薦商品清單。）",List.of());
                return new SavedResult(r.getInt("answer_id"),r.getInt("quiz_id"),r.getString("quiz_name"),r.getTimestamp("created_at").toLocalDateTime(),result);
            },memberId,beforeId==null?Integer.MAX_VALUE:beforeId);
        return new ResultPage(List.copyOf(rows.subList(0,Math.min(20,rows.size()))),rows.size()>20);
    }
    private record Option(String text,Map<String,Integer> scores) {}
    private record Question(Integer id,String text,Map<String,Option> options) {}
    private record Outcome(Integer id,String name,String description) {}
    public static final class Run {
        public final String token=UUID.randomUUID().toString();
        private final Integer memberId,quizId;
        private final String name,description;
        private final List<Question> questions;
        private final Instant expires=Instant.now().plusSeconds(1800);
        public Result completed;
        private Run(Integer memberId,Theme theme,List<Question> questions){this.memberId=memberId;quizId=theme.quizId();name=theme.name();description=theme.description();this.questions=questions;}
        public StartView view(){return new StartView(token,quizId,name,description,questions.stream().map(q->new QuestionView(q.id(),q.text(),q.options().entrySet().stream().map(e->new Choice(e.getKey(),e.getValue().text())).toList())).toList());}
        public boolean belongsTo(Integer member,Integer quiz){return memberId.equals(member)&&quizId.equals(quiz)&&Instant.now().isBefore(expires);}
    }
    public static boolean isQuizRequest(String text){
        if(text==null)return false;String value=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+","");
        if(value.matches(".*(?:不要|不用|不想|不需要).*(?:推薦|測驗).*"))return false;
        return value.matches(".*(?:推薦.*(?:水果|果物)|(?:水果|果物).*推薦|水果測驗|心理測驗|小測驗).*" );
    }
    @Transactional(readOnly=true)
    public List<Theme> themes(){
        return jdbc.query("SELECT q.quiz_id,q.quiz_name,q.quiz_desc FROM quiz q WHERE q.status=1 "
            +"AND (SELECT COUNT(*) FROM quiz_question x WHERE x.quiz_id=q.quiz_id AND x.status=1)=5 "
            +"AND (SELECT COUNT(*) FROM quiz_result r WHERE r.quiz_id=q.quiz_id AND r.status=1)>=5 ORDER BY q.quiz_id",
            (r,n)->new Theme(r.getInt("quiz_id"),r.getString("quiz_name"),r.getString("quiz_desc"),5));
    }
    @Transactional(readOnly=true)
    public Run start(Integer memberId,Integer quizId){
        var theme=themes().stream().filter(t->t.quizId().equals(quizId)).findFirst().orElseThrow(()->new IllegalArgumentException("這個測驗目前無法使用，請重新選擇。"));
        var questions=jdbc.query("SELECT question_id,question_text,options FROM quiz_question WHERE quiz_id=? AND status=1 ORDER BY sort_order,question_id",
            (r,n)->new Question(r.getInt("question_id"),r.getString("question_text"),options(r.getString("options"))),quizId);
        if(questions.size()!=5)throw new IllegalArgumentException("測驗題目已更新，請重新開始。");
        return new Run(memberId,theme,List.copyOf(questions));
    }
    private Map<String,Option> options(String text){
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
    @Transactional
    public Result finish(Integer memberId,Integer quizId,Run run,Map<Integer,String> answers){
        if(run==null||!run.belongsTo(memberId,quizId))throw new IllegalArgumentException("測驗已過期，請重新開始。");
        if(answers==null||answers.size()!=run.questions.size()||!answers.keySet().equals(run.questions.stream().map(Question::id).collect(java.util.stream.Collectors.toSet())))throw new IllegalArgumentException("請完成所有題目再查看結果。");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE quiz_id=? AND status=1",Integer.class,quizId)!=1)throw new IllegalArgumentException("這個測驗目前無法使用。");
        var scores=new TreeMap<String,Integer>();
        for(var question:run.questions){var option=question.options().get(answers.get(question.id()));if(option==null)throw new IllegalArgumentException("答案選項不正確，請重新作答。");option.scores().forEach((fruit,score)->scores.merge(fruit,score,Integer::sum));}
        var outcomes=jdbc.query("SELECT result_id,result_name,result_desc FROM quiz_result WHERE quiz_id=? AND status=1 ORDER BY result_id",
            (r,n)->new Outcome(r.getInt("result_id"),r.getString("result_name"),r.getString("result_desc")),quizId);
        var products=catalog.getProducts();
        // Each theme's configured outcomes define its own fruit pool; history never changes the ranking.
        var ranked=scores.entrySet().stream().filter(e->e.getValue()>0).sorted(
            Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).toList();
        var recommendations=new ArrayList<Recommendation>();Outcome winning=null;var usedProducts=new HashSet<Integer>();
        for(var score:ranked){String name=FRUITS.get(score.getKey());var outcome=outcomes.stream().filter(o->o.name().equals(name)).findFirst().orElse(null);if(outcome==null)continue;
            // Fill from the highest-scoring fruit in this theme before considering its next ranked fruit.
            var candidates=products.stream().filter(p->!usedProducts.contains(p.productId())&&matchesFruit(p.name(),score.getKey()))
                .filter(p->p.skus().stream().anyMatch(ChatQuizService::buyable))
                .sorted(Comparator.comparing((FrontCatalogService.ProductView p)->p.averageRating()==null?0.0:p.averageRating()).reversed().thenComparing(FrontCatalogService.ProductView::productId)).toList();
            for(var product:candidates){
                if(!usedProducts.add(product.productId()))continue;
                var sku=product.skus().stream().filter(ChatQuizService::buyable).min(Comparator.comparing(FrontCatalogService.SkuView::price).thenComparing(FrontCatalogService.SkuView::skuId)).orElseThrow();
                recommendations.add(new Recommendation(name,outcome.description(),product.productId(),product.name(),sku.price()));if(winning==null)winning=outcome;
                if(recommendations.size()==5)break;
            }
            if(recommendations.size()==5)break;
        }
        if(winning==null)throw new IllegalArgumentException("目前暫無適合的上架商品，請稍後再試。");
        var result=new Result("今天最合拍的果物："+winning.name(),winning.description(),List.copyOf(recommendations));
        var storedScores=new TreeMap<String,Object>();storedScores.putAll(scores);storedScores.put("_result",result);
        jdbc.update("INSERT INTO chat_quiz_answer (member_id,quiz_id,answer_data,score_data,result_id,created_at) VALUES (?,?,?,?,?,?)",
            memberId,quizId,json.writeValueAsString(answers),json.writeValueAsString(storedScores),winning.id(),LocalDateTime.now(ZoneId.of("Asia/Taipei")));
        return result;
    }
    private static boolean matchesFruit(String productName,String fruit){
        if(productName==null)return false;
        return switch(fruit){
            case "banana" -> productName.contains("香蕉")||productName.contains("山蕉");
            case "guava" -> productName.contains("芭樂")||productName.contains("帝王拔");
            case "pear" -> productName.contains("梨")&&!productName.contains("鳳梨");
            case "persimmon" -> productName.contains("柿");
            default -> productName.contains(FRUITS.get(fruit));
        };
    }
    private static boolean buyable(FrontCatalogService.SkuView sku){return sku.stock()!=null&&sku.stock()>0&&sku.price()!=null&&sku.price()>0&&sku.skuStatus()!=null&&Set.of(1,2,3,6).contains(sku.skuStatus());}
}
