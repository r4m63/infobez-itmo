package com.itmo.infobez.web;

import com.itmo.infobez.security.LoginAttemptService;
import com.itmo.infobez.security.TokenService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempts;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
                          LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (loginAttempts.isBlocked(request.username())) {
            log.warn("Blocked login attempt for user '{}': too many failures", forLog(request.username()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            loginAttempts.recordFailure(request.username());
            log.warn("Failed login attempt for user '{}'", forLog(request.username()));
            // Единый ответ для неверного логина и неверного пароля.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        loginAttempts.recordSuccess(request.username());
        TokenService.IssuedToken token = tokenService.issue(authentication);
        return ResponseEntity.ok(new LoginResponse(token.token(), "Bearer", token.expiresInSeconds()));
    }

    /** Убирает CR/LF из пользовательского ввода, чтобы нельзя было подделать строки лога (log forging). */
    private static String forLog(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", "_");
    }
}
