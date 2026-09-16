package com.itmo.infobez.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "author")
    Page<Post> findAllBy(Pageable pageable);

    /**
     * Поиск по заголовку. Значение подставляется как связанный параметр (:query),
     * а не конкатенацией строки — SQL-инъекция невозможна (OWASP A03).
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Post> search(@Param("query") String query, Pageable pageable);
}
