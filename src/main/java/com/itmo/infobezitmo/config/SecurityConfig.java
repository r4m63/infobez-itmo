package com.itmo.infobezitmo.config;

import com.itmo.infobezitmo.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                // Stateless REST API: нет сессий и cookie, CSRF-токен неприменим.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login").permitAll()
                        // Всё остальное — только с валидным JWT.
                        .anyRequest().authenticated())
                // JWT-middleware выполняется до стандартного фильтра логина.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .contentTypeOptions(c -> { }))
                .build();
    }

    /** bcrypt, cost 12: медленный адаптивный хеш с солью — перебор по утёкшей базе непрактичен. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Пользователи учебного примера хранятся в памяти. Пароли берутся из переменных окружения
     * и сразу превращаются в bcrypt-хеши — в открытом виде нигде не сохраняются.
     */
    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder,
                                          @Value("${app.demo.admin-password}") String adminPassword,
                                          @Value("${app.demo.user-password}") String userPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password(encoder.encode(adminPassword)).roles("ADMIN").build(),
                User.withUsername("user").password(encoder.encode(userPassword)).roles("USER").build());
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        // Одинаковый ответ для "нет такого пользователя" и "неверный пароль" — нельзя перебирать логины.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }
}
