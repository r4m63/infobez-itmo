package com.itmo.infobez.config;

import com.itmo.infobez.post.Post;
import com.itmo.infobez.post.PostRepository;
import com.itmo.infobez.user.AppUser;
import com.itmo.infobez.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Демонстрационные учётные записи. Профиль "dev" — чтобы они никогда не создавались в проде.
 * Пароли берутся из переменных окружения, в репозитории их нет.
 */
@Configuration
@Profile("dev")
public class DevDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    @Bean
    ApplicationRunner seedDemoData(AppUserRepository users, PostRepository posts, PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            String adminPassword = required("DEMO_ADMIN_PASSWORD");
            String userPassword = required("DEMO_USER_PASSWORD");

            AppUser admin = users.save(new AppUser("admin", encoder.encode(adminPassword), "ADMIN"));
            AppUser user = users.save(new AppUser("user", encoder.encode(userPassword), "USER"));

            posts.save(new Post("Первый пост", "Данные доступны только по валидному JWT.", admin));
            posts.save(new Post("Второй пост", "Пароли хранятся в виде bcrypt-хешей.", user));
            log.info("Seeded demo users: admin, user");
        };
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " must be set for the dev profile");
        }
        return value;
    }
}
