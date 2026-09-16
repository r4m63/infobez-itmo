package com.itmo.infobez.post;

import java.time.Instant;

public record PostResponse(Long id, String title, String content, String author, Instant createdAt) {
}
