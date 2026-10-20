package com.fruitude.faq.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqCategoryRepository extends JpaRepository<FaqCategory,Integer> {
    List<FaqCategory> findAllByOrderByFaqCategoryIdAsc();
    boolean existsByParentCategory_FaqCategoryId(Integer id);
    // Explicitly handle NULL parents: root-level duplicate names are rejected too.
    @Query("""
        select (count(c)>0) from FaqCategory c left join c.parentCategory parent
        where c.categoryName=:name
          and ((:parentId is null and parent is null) or parent.faqCategoryId=:parentId)
          and (:excludedId is null or c.faqCategoryId<>:excludedId)
        """)
    boolean existsSiblingName(@Param("name") String name,@Param("parentId") Integer parentId,@Param("excludedId") Integer excludedId);
}
