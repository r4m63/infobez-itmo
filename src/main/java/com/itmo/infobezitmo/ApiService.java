package com.itmo.infobezitmo;

import com.itmo.infobezitmo.model.Post;
import com.itmo.infobezitmo.security.JwtService;
import jakarta.annotation.PostConstruct;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.List;

/** Единственный сервис: логин, чтение данных, создание поста. */
@Service
public class ApiService {

    /** DTO ответа. Все строки экранированы HtmlUtils.htmlEscape — в ответ не попадает сырой HTML (защита от XSS). */
    public record PostDto(Long id, String title, String content, String author, Instant createdAt) {
        static PostDto from(Post p) {
            return new PostDto(p.getId(), HtmlUtils.htmlEscape(p.getTitle()), HtmlUtils.htmlEscape(p.getContent()),
                    HtmlUtils.htmlEscape(p.getAuthor()), p.getCreatedAt());
        }
    }

    public record Token(String accessToken, String tokenType, long expiresIn) {
    }

    private final PostRepository posts;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public ApiService(PostRepository posts, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.posts = posts;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Демо-данные для учебного примера. */
    @PostConstruct
    void seed() {
        if (posts.count() == 0) {
            posts.save(Post.builder().title("Первый пост").content("Данные доступны только по валидному JWT.").author("admin").build());
            posts.save(Post.builder().title("Второй пост").content("Пароли хранятся в виде bcrypt-хешей.").author("user").build());
        }
    }

    /**
     * Проверяет логин/пароль (bcrypt через AuthenticationManager) и выпускает JWT.
     * @throws org.springframework.security.core.AuthenticationException при неверных данных
     */
    public Token login(String username, String password) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        return new Token(jwtService.issue(auth), "Bearer", jwtService.ttlSeconds());
    }

    public List<PostDto> getData(String query) {
        List<Post> found = (query == null || query.isBlank())
                ? posts.findAllByOrderByCreatedAtDesc()
                : posts.searchByTitle(query.trim());
        return found.stream().map(PostDto::from).toList();
    }

    /** Автор берётся из проверенного JWT, а не из тела запроса. */
    public PostDto createPost(String author, String title, String content) {
        return PostDto.from(posts.save(Post.builder().title(title.trim()).content(content.trim()).author(author).build()));
    }
}
