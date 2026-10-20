package com.fruitude.quiz.model;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatQuizAnswerRepository extends JpaRepository<ChatQuizAnswer,Integer> {
    @Query("""
        select a from ChatQuizAnswer a join fetch a.quiz q left join fetch a.quizResult r
        where a.member.memberId=:memberId and a.answerId<:beforeId
        order by a.answerId desc
        """)
    List<ChatQuizAnswer> findMemberHistory(@Param("memberId") Integer memberId,
            @Param("beforeId") Integer beforeId,Pageable pageable);
}
