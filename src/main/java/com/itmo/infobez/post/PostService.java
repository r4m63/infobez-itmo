package com.itmo.infobez.post;

import com.itmo.infobez.user.AppUser;
import com.itmo.infobez.user.AppUserRepository;
import com.itmo.infobez.web.HtmlSanitizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository posts;
    private final AppUserRepository users;
    private final HtmlSanitizer sanitizer;

    public PostService(PostRepository posts, AppUserRepository users, HtmlSanitizer sanitizer) {
        this.posts = posts;
        this.users = users;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> list(String query, Pageable pageable) {
        Page<Post> page = (query == null || query.isBlank())
                ? posts.findAllBy(pageable)
                : posts.search(query, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public PostResponse create(CreatePostRequest request, String username) {
        AppUser author = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        // Разметка вырезается до сохранения: в БД лежит только текст.
        Post saved = posts.save(new Post(
                sanitizer.sanitizeInput(request.title()),
                sanitizer.sanitizeInput(request.content()),
                author));
        return toResponse(saved);
    }

    /** Все строковые поля экранируются перед отправкой клиенту. */
    private PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                sanitizer.escapeOutput(post.getTitle()),
                sanitizer.escapeOutput(post.getContent()),
                sanitizer.escapeOutput(post.getAuthor().getUsername()),
                post.getCreatedAt());
    }
}
