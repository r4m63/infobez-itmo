package com.itmo.infobezitmo.web;

import com.itmo.infobezitmo.post.CreatePostRequest;
import com.itmo.infobezitmo.post.PostResponse;
import com.itmo.infobezitmo.post.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Все эндпоинты /api/** требуют валидный JWT (см. SecurityConfig). */
@RestController
@RequestMapping("/api")
@Validated
public class DataController {

    private final PostService posts;

    public DataController(PostService posts) {
        this.posts = posts;
    }

    /** GET /api/data — список постов, необязательный поиск по заголовку ?query=... */
    @GetMapping("/data")
    public Map<String, Object> data(@RequestParam(required = false) @Size(max = 100) String query) {
        List<PostResponse> items = posts.list(query);
        return Map.of("items", items, "total", items.size());
    }

    /** POST /api/posts — третий метод: создание поста от имени владельца токена. */
    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request,
                                                   Authentication authentication) {
        PostResponse created = posts.create(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
