package com.fruitude.quiz.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion,Integer> {
    List<QuizQuestion> findByQuiz_QuizIdAndStatusOrderBySortOrderAscQuestionIdAsc(Integer quizId,Integer status);
}
