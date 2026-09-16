package com.itmo.infobezitmo.controller;

import com.itmo.infobezitmo.service.ApiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Единственный контроллер с тремя методами API. */
@RestController
@Validated
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 128) String password) {
        /** Пароль не должен попадать в логи. */
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=***]";
        }
    }

    public record PostRequest(@NotBlank @Size(max = 200) String title,
                              @NotBlank @Size(max = 4000) String content) {
    }

    private final ApiService service;

    public ApiController(ApiService service) {
        this.service = service;
    }

    /** 1) POST /auth/login — логин и пароль -> JWT. Единственный публичный эндпоинт. */
    @PostMapping("/auth/login")
    public ApiService.Token login(@Valid @RequestBody LoginRequest request) {
        try {
            return service.login(request.username(), request.password());
        } catch (AuthenticationException ex) {
            // Одинаковый ответ для неизвестного логина и неверного пароля; логин очищен от \r\n (log forging).
            log.warn("Failed login attempt for user '{}'", request.username().replaceAll("[\\r\\n]", "_"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }

    /** 2) GET /api/data — список постов, только с валидным JWT (см. SecurityConfig). */
    @GetMapping("/api/data")
    public Map<String, Object> data(@RequestParam(required = false) @Size(max = 100) String query) {
        List<ApiService.PostDto> items = service.getData(query);
        return Map.of("items", items, "total", items.size());
    }

    /** 3) POST /api/posts — создание поста от имени владельца токена. */
    @PostMapping("/api/posts")
    public ResponseEntity<ApiService.PostDto> createPost(@Valid @RequestBody PostRequest request,
                                                         Authentication authentication) {
        ApiService.PostDto created = service.createPost(authentication.getName(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
