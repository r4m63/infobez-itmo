package com.itmo.infobezitmo.config;

import com.itmo.infobezitmo.post.Post;
import com.itmo.infobezitmo.post.PostRepository;
import com.itmo.infobezitmo.user.AppUser;
import com.itmo.infobezitmo.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Демо-пользователи для учебного примера. Пароли берутся из окружения и сразу хешируются bcrypt. */
@Configuration
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    @ConfigurationProperties(prefix = "app.demo")
    public record DemoProperties(String adminPassword, String userPassword) {
    }

    @Bean
    ApplicationRunner seedDemoData(AppUserRepository users, PostRepository posts,
                                   PasswordEncoder encoder, DemoProperties demo) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            AppUser admin = users.save(new AppUser("admin", encoder.encode(demo.adminPassword()), "ADMIN"));
            AppUser user = users.save(new AppUser("user", encoder.encode(demo.userPassword()), "USER"));
            posts.save(new Post("Первый пост", "Данные доступны только по валидному JWT.", admin));
            posts.save(new Post("Второй пост", "Пароли хранятся в виде bcrypt-хешей.", user));
            log.info("Seeded demo users: admin, user");
        };
    }
}
