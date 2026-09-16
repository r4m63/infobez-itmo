package com.itmo.infobez;

import com.itmo.infobez.user.AppUser;
import com.itmo.infobez.user.AppUserRepository;
import com.itmo.infobez.web.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "S3cret-p4ssword!";

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        if (users.findByUsername("tester").isEmpty()) {
            users.save(new AppUser("tester", encoder.encode(PASSWORD), "USER"));
        }
    }

    @Test
    void loginReturnsToken() {
        assertThat(login()).isNotBlank();
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"tester","password":"wrong-password"}""")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginWithUnknownUserReturnsSameResponseAsWrongPassword() {
        client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"no-such-user","password":"wrong-password"}""")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Invalid username or password");
    }

    @Test
    void dataRequiresAuthentication() {
        client.get().uri("/api/data").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void dataIsAvailableWithToken() {
        client.get().uri("/api/data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.items").isArray();
    }

    @Test
    void forgedTokenIsRejected() {
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.not-a-valid-signature";
        client.get().uri("/api/data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createPostUsesAuthorFromToken() {
        client.post().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"Заголовок","content":"Текст поста"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.author").isEqualTo("tester");
    }

    @Test
    void createPostRejectsInvalidPayload() {
        client.post().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"","content":""}""")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void htmlInPostIsSanitizedAndEscaped() {
        client.post().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"<script>alert(1)</script>XSS","content":"<img src=x onerror=alert(1)>текст"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.title").isEqualTo("XSS")
                .jsonPath("$.content").isEqualTo("текст");
    }

    @Test
    void sqlInjectionInSearchIsTreatedAsPlainText() {
        client.get().uri("/api/data?query=%27%20OR%20%271%27%3D%271")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.items").isEmpty();
    }

    @Test
    void repeatedFailedLoginsAreThrottled() {
        for (int i = 0; i < 5; i++) {
            client.post().uri("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"username":"brute-force-target","password":"wrong-password"}""")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
        client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"brute-force-target","password":"wrong-password"}""")
                .exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
    }

    private String login() {
        LoginResponse response = client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"tester","password":"%s"}""".formatted(PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();
        return response.accessToken();
    }
}
