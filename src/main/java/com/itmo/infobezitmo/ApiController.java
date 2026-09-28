package com.itmo.infobezitmo;

import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@Validated
public class ApiController {

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password
    ) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=***]";
        }
    }

    public record PostRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String content
    ) {
    }

    public record PostDto(long id, String title, String content, String author, Instant createdAt) {
    }

    public record Token(String accessToken, String tokenType, long expiresIn) {
    }

    private final PostRepository posts;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final Map<String, String> users;
    private final String issuer;
    private final long ttlSeconds;
    private final JwtEncoder jwtEncoder;

    public ApiController(PostRepository posts,
                         JwtEncoder jwtEncoder,
                         @Value("${app.demo.admin-password-hash}") String adminPasswordHash,
                         @Value("${app.demo.user-password-hash}") String userPasswordHash,
                         @Value("${app.jwt.issuer}") String issuer,
                         @Value("${app.jwt.ttl}") Duration ttl) {
        this.posts = posts;
        this.jwtEncoder = jwtEncoder;
        this.users = Map.of("admin", adminPasswordHash, "user", userPasswordHash);
        this.issuer = issuer;
        this.ttlSeconds = ttl.toSeconds();
    }

    @PostConstruct
    void init() {
        if (posts.count() == 0) {
            posts.save(new Post("Первый пост", "Данные доступны только по валидному JWT.", "admin"));
            posts.save(new Post("Второй пост", "Пароли хранятся в виде bcrypt-хешей.", "user"));
        }
    }

    @PostMapping("/auth/login")
    public Token login(@Valid @RequestBody LoginRequest request) {
        boolean correctPassword = passwords.matches(request.password(), users.getOrDefault(request.username(), users.get("admin")));
        if (!correctPassword || !users.containsKey(request.username())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(request.username())
                .issuedAt(now).expiresAt(now.plusSeconds(ttlSeconds)).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(value, "Bearer", ttlSeconds);
    }

    @GetMapping("/api/data")
    public Map<String, Object> data(@RequestParam(required = false) @Size(max = 100) String query) {
        List<Post> found = query == null || query.isBlank()
                ? posts.findAllByOrderByCreatedAtDesc()
                : posts.searchByTitle(query.trim());
        List<PostDto> safe = found.stream().map(ApiController::escape).toList();
        return Map.of("items", safe, "total", safe.size());
    }

    @PostMapping("/api/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto createPost(@Valid @RequestBody PostRequest request, Authentication authentication) {
        Post post = posts.save(new Post(request.title().trim(), request.content().trim(), authentication.getName()));
        return escape(post);
    }

    private static PostDto escape(Post post) {
        return new PostDto(post.getId(), HtmlUtils.htmlEscape(post.getTitle()),
                HtmlUtils.htmlEscape(post.getContent()), HtmlUtils.htmlEscape(post.getAuthor()), post.getCreatedAt());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail statusException(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ProblemDetail invalidInput() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    }
}
