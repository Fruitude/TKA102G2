package com.fruitude.faq.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FaqRepository extends JpaRepository<Faq,Integer> {
    // A disabled ancestor hides all descendants. The existing recursive SQL stays in the repository.
    @Query(value="""
        WITH RECURSIVE active_categories AS (
          SELECT faq_category_id FROM faq_category WHERE parent_category_id IS NULL AND status=1
          UNION ALL
          SELECT c.faq_category_id FROM faq_category c JOIN active_categories p
            ON c.parent_category_id=p.faq_category_id WHERE c.status=1
        )
        SELECT f.* FROM faq f JOIN active_categories c ON c.faq_category_id=f.faq_category_id
        ORDER BY f.sort_order,f.faq_id
        """,nativeQuery=true)
    List<Faq> findAvailableForReply();

    @Query("select f from Faq f order by f.faqCategory.faqCategoryId,f.sortOrder,f.faqId")
    List<Faq> findForAdministration();
    @Query("select f from Faq f where f.faqCategory.faqCategoryId=:id order by f.sortOrder,f.faqId")
    List<Faq> findByCategoryForAdministration(@org.springframework.data.repository.query.Param("id") Integer id);
    boolean existsByFaqCategory_FaqCategoryId(Integer id);
    boolean existsByFaqCategory_FaqCategoryIdAndQuestion(Integer id,String question);
    boolean existsByFaqCategory_FaqCategoryIdAndQuestionAndFaqIdNot(Integer id,String question,Integer excludedId);
    @Query("select coalesce(max(f.sortOrder),0) from Faq f where f.faqCategory.faqCategoryId=:id")
    int findMaxSortOrder(@org.springframework.data.repository.query.Param("id") Integer id);
}
