package com.itmo.infobez.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String content) {
}
