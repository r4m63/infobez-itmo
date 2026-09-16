package com.itmo.infobez.web;

import com.itmo.infobez.post.CreatePostRequest;
import com.itmo.infobez.post.PostResponse;
import com.itmo.infobez.post.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api")
@Validated
public class DataController {

    private final PostService posts;
    private final HtmlSanitizer sanitizer;

    public DataController(PostService posts, HtmlSanitizer sanitizer) {
        this.posts = posts;
        this.sanitizer = sanitizer;
    }

    /** Данные доступны только аутентифицированным пользователям (см. SecurityConfig). */
    @GetMapping("/data")
    public PageResponse<PostResponse> data(
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Сортировка задана в коде: имя поля не приходит от пользователя (иначе — вектор инъекции).
        return PageResponse.of(posts.list(
                sanitizer.sanitizeInput(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /** Третий метод: создание поста от имени текущего пользователя. */
    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request,
                                                   @AuthenticationPrincipal Jwt principal) {
        // Автор берётся из токена, а не из тела запроса — нельзя писать от чужого имени (OWASP A01).
        PostResponse created = posts.create(request, principal.getSubject());
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/posts/{id}").build(created.id()))
                .body(created);
    }
}
