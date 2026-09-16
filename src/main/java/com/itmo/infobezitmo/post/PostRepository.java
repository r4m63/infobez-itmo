package com.itmo.infobezitmo.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.createdAt DESC")
    List<Post> findAllWithAuthor();

    /**
     * Защита от SQL-инъекций: значение приходит как именованный параметр :query.
     * Hibernate передаёт его в PreparedStatement отдельно от текста запроса,
     * поэтому любые кавычки и ключевые слова внутри — просто часть строки поиска.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author "
            + "WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.createdAt DESC")
    List<Post> searchByTitle(@Param("query") String query);
}
