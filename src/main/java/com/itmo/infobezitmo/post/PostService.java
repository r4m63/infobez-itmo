package com.itmo.infobezitmo.post;

import com.itmo.infobezitmo.user.AppUser;
import com.itmo.infobezitmo.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PostService {

    private final PostRepository posts;
    private final AppUserRepository users;

    public PostService(PostRepository posts, AppUserRepository users) {
        this.posts = posts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<PostResponse> list(String query) {
        List<Post> found = (query == null || query.isBlank())
                ? posts.findAllWithAuthor()
                : posts.searchByTitle(query.trim());
        return found.stream().map(PostResponse::from).toList();
    }

    /** Автор берётся из проверенного JWT (subject), а не из тела запроса. */
    @Transactional
    public PostResponse create(String authorUsername, CreatePostRequest request) {
        AppUser author = users.findByUsername(authorUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        Post saved = posts.save(new Post(request.title().trim(), request.content().trim(), author));
        return PostResponse.from(saved);
    }
}
