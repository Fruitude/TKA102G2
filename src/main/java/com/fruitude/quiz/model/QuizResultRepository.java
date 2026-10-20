package com.fruitude.quiz.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizResultRepository extends JpaRepository<QuizResult,Integer> {
    List<QuizResult> findByQuiz_QuizIdAndStatusOrderByResultIdAsc(Integer quizId,Integer status);
}
