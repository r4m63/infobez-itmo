package com.itmo.infobezitmo.post;

import org.springframework.web.util.HtmlUtils;

import java.time.Instant;

/** DTO ответа. Строковые поля экранируются при создании — в ответ не попадает сырой HTML (защита от XSS). */
public record PostResponse(Long id, String title, String content, String author, Instant createdAt) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                HtmlUtils.htmlEscape(post.getTitle()),
                HtmlUtils.htmlEscape(post.getContent()),
                HtmlUtils.htmlEscape(post.getAuthor().getUsername()),
                post.getCreatedAt());
    }
}
