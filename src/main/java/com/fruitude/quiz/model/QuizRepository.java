package com.fruitude.quiz.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizRepository extends JpaRepository<Quiz,Integer> {
    @Query("""
        select q from Quiz q where q.status=1
          and (select count(x) from QuizQuestion x where x.quiz=q and x.status=1)=5
          and (select count(r) from QuizResult r where r.quiz=q and r.status=1)>=5
        order by q.quizId
        """)
    List<Quiz> findPlayableThemes();

    boolean existsByQuizIdAndStatus(Integer quizId,Integer status);
}
