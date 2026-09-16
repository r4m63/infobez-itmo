package com.itmo.infobez.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простейшая защита от подбора паролей (OWASP A07): после N неудачных попыток
 * логин временно блокируется. Состояние в памяти — для одного инстанса этого достаточно;
 * в распределённом развёртывании нужен общий кэш (Redis).
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String username) {
        Attempts current = attempts.get(key(username));
        if (current == null) {
            return false;
        }
        if (current.lockedUntil().isBefore(Instant.now())) {
            attempts.remove(key(username));
            return false;
        }
        return current.count() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String username) {
        attempts.compute(key(username), (ignored, current) -> {
            int count = current == null || current.lockedUntil().isBefore(Instant.now()) ? 1 : current.count() + 1;
            return new Attempts(count, Instant.now().plus(LOCK_DURATION));
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    private record Attempts(int count, Instant lockedUntil) {
    }
}
