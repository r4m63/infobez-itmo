package com.itmo.infobezitmo.repository;

import com.itmo.infobezitmo.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Единственный репозиторий. SQL нигде не собирается конкатенацией строк. */
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findAllByOrderByCreatedAtDesc();

    /**
     * Защита от SQL-инъекций: значение приходит как именованный параметр :query.
     * Hibernate передаёт его в PreparedStatement отдельно от текста запроса,
     * поэтому кавычки и ключевые слова внутри — просто часть строки поиска.
     */
    @Query("SELECT p FROM Post p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.createdAt DESC")
    List<Post> searchByTitle(@Param("query") String query);
}
