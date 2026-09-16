package com.itmo.infobez.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String password) {

    /** Пароль не должен попадать в логи и сообщения об ошибках. */
    @Override
    public String toString() {
        return "LoginRequest[username=%s, password=***]".formatted(username);
    }
}
