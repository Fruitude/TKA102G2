package com.fruitude.quiz.model;

import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fruitude.member.model.MemberVO;
import com.fruitude.quiz.model.QuizScoringService.Question;
import tools.jackson.databind.json.JsonMapper;

/** Coordinates persisted quiz configuration, scoring, recommendations and private member history. */
@Service
public class ChatQuizService {
    private final QuizRepository quizzes;
    private final QuizQuestionRepository questions;
    private final QuizResultRepository results;
    private final ChatQuizAnswerRepository answers;
    private final QuizScoringService scoring;
    private final QuizRecommendationService recommendations;
    private final JsonMapper json=JsonMapper.builder().build();
    @PersistenceContext private EntityManager entityManager;

    public ChatQuizService(QuizRepository quizzes,QuizQuestionRepository questions,QuizResultRepository results,
            ChatQuizAnswerRepository answers,QuizScoringService scoring,QuizRecommendationService recommendations){
        this.quizzes=quizzes;this.questions=questions;this.results=results;this.answers=answers;
        this.scoring=scoring;this.recommendations=recommendations;
    }
    public record Theme(Integer quizId,String name,String description,int questionCount) {}
    public record Choice(String key,String text) {}
    public record QuestionView(Integer questionId,String text,List<Choice> choices) {}
    public record StartView(String token,Integer quizId,String name,String description,List<QuestionView> questions) {}
    public record Submission(String token,Map<Integer,String> answers) {}
    public record Recommendation(String fruitName,String description,Integer productId,String productName,Integer price) {}
    public record Result(String headline,String description,List<Recommendation> recommendations) {}
    public record SavedResult(Integer answerId,Integer quizId,String theme,LocalDateTime createdAt,Result result) {}
    public record ResultPage(List<SavedResult> results,boolean hasMore) {}
    public record RunSnapshot(String token,Integer memberId,Integer quizId,String name,String description,List<Question> questions,Instant expires) {}
    public static Run restore(RunSnapshot snapshot){return new Run(snapshot);}
    public static final class Run {
        public final String token;
        private final Integer memberId,quizId;
        private final String name,description;
        private final List<Question> questions;
        private final Instant expires;
        public Result completed;
        private Run(Integer memberId,Theme theme,List<Question> questions){this(new RunSnapshot(UUID.randomUUID().toString(),memberId,theme.quizId(),theme.name(),theme.description(),questions,Instant.now().plusSeconds(1800)));}
        private Run(RunSnapshot snapshot){token=snapshot.token();memberId=snapshot.memberId();quizId=snapshot.quizId();name=snapshot.name();description=snapshot.description();questions=snapshot.questions();expires=snapshot.expires();}
        public RunSnapshot snapshot(){return new RunSnapshot(token,memberId,quizId,name,description,questions,expires);}
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
        return quizzes.findPlayableThemes().stream().map(q->new Theme(q.getQuizId(),q.getQuizName(),q.getQuizDesc(),5)).toList();
    }

    @Transactional(readOnly=true)
    public Run start(Integer memberId,Integer quizId){
        var theme=themes().stream().filter(t->t.quizId().equals(quizId)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("這個測驗目前無法使用，請重新選擇。"));
        var configured=questions.findByQuiz_QuizIdAndStatusOrderBySortOrderAscQuestionIdAsc(quizId,1).stream()
            .map(q->new Question(q.getQuestionId(),q.getQuestionText(),scoring.options(q.getOptions()))).toList();
        if(configured.size()!=5)throw new IllegalArgumentException("測驗題目已更新，請重新開始。");
        return new Run(memberId,theme,List.copyOf(configured));
    }

    @Transactional
    public Result finish(Integer memberId,Integer quizId,Run run,Map<Integer,String> submitted){
        if(run==null||!run.belongsTo(memberId,quizId))throw new IllegalArgumentException("測驗已過期，請重新開始。");
        var scores=scoring.score(run.questions,submitted);
        if(!quizzes.existsByQuizIdAndStatus(quizId,1))throw new IllegalArgumentException("這個測驗目前無法使用。");
        var selected=recommendations.recommend(scores,results.findByQuiz_QuizIdAndStatusOrderByResultIdAsc(quizId,1));
        var storedScores=new TreeMap<String,Object>();storedScores.putAll(scores);storedScores.put("_result",selected.result());
        var answer=new ChatQuizAnswer();
        answer.setMember(entityManager.getReference(MemberVO.class,memberId));
        answer.setQuiz(quizzes.getReferenceById(quizId));
        answer.setQuizResult(results.getReferenceById(selected.resultId()));
        answer.setAnswerData(json.writeValueAsString(submitted));answer.setScoreData(json.writeValueAsString(storedScores));
        answer.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Taipei")));answers.save(answer);
        return selected.result();
    }

    /** Same scoring and recommendation rules, with no member reference or SQL writes. */
    @Transactional(readOnly=true)
    public Result finishGuest(Integer quizId,Run run,Map<Integer,String> submitted){
        if(run==null||!run.belongsTo(0,quizId))throw new IllegalArgumentException("測驗已過期，請重新開始。");
        var scores=scoring.score(run.questions,submitted);
        if(!quizzes.existsByQuizIdAndStatus(quizId,1))throw new IllegalArgumentException("這個測驗目前無法使用。");
        return recommendations.recommend(scores,results.findByQuiz_QuizIdAndStatusOrderByResultIdAsc(quizId,1)).result();
    }

    /** Only this member's records are loaded; quiz history never enters the staff message stream. */
    @Transactional(readOnly=true)
    public ResultPage history(Integer memberId,Integer beforeId){
        if(beforeId!=null&&beforeId<1)throw new IllegalArgumentException("測驗紀錄編號不正確。");
        var rows=answers.findMemberHistory(memberId,beforeId==null?Integer.MAX_VALUE:beforeId,PageRequest.of(0,21));
        var saved=rows.stream().limit(20).map(a->{
            var raw=json.readValue(a.getScoreData(),Map.class);Object snapshot=raw.get("_result");
            var outcome=a.getQuizResult();
            var result=snapshot instanceof Map<?,?>?json.readValue(json.writeValueAsString(snapshot),Result.class):
                new Result("你的果物測驗結果："+(outcome==null?"果物靈感":outcome.getResultName()),
                    (outcome==null?"":Objects.toString(outcome.getResultDesc(),""))+"（此筆為早期紀錄，未保存當時的推薦商品清單。）",List.of());
            return new SavedResult(a.getAnswerId(),a.getQuiz().getQuizId(),a.getQuiz().getQuizName(),a.getCreatedAt(),result);
        }).toList();
        return new ResultPage(saved,rows.size()>20);
    }
}
