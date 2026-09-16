package com.itmo.infobezitmo;

import com.itmo.infobezitmo.web.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-value-that-is-long-enough-32-chars",
        "app.demo.admin-password=Admin-test-password",
        "app.demo.user-password=User-test-password"
})
class ApiSecurityTest {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void loginReturnsJwt() {
        String token = login("admin", "Admin-test-password");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"admin","password":"wrong"}""")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unknownUserGetsSameResponseAsWrongPassword() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"nobody","password":"wrong"}""")
                .exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Invalid username or password");
    }

    @Test
    void dataWithoutTokenIsForbidden() {
        client.get().uri("/api/data").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void dataWithTokenIsAvailable() {
        client.get().uri("/api/data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("user", "User-test-password"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items").isArray();
    }

    @Test
    void forgedTokenIsRejected() {
        client.get().uri("/api/data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.forged")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void createPostTakesAuthorFromToken() {
        client.post().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("user", "User-test-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"Заголовок","content":"Текст"}""")
                .exchange().expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody().jsonPath("$.author").isEqualTo("user");
    }

    @Test
    void htmlInResponseIsEscaped() {
        client.post().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("user", "User-test-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"<script>alert(1)</script>","content":"<img src=x onerror=alert(1)>"}""")
                .exchange().expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody()
                .jsonPath("$.title").isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;")
                .jsonPath("$.content").isEqualTo("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void sqlInjectionInSearchIsHarmless() {
        client.get().uri("/api/data?query=%27%20OR%20%271%27%3D%271")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("user", "User-test-password"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.total").isEqualTo(0);
    }

    @Test
    void invalidPayloadIsBadRequest() {
        client.post().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("user", "User-test-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"","content":""}""")
                .exchange().expectStatus().isBadRequest();
    }

    private String login(String username, String password) {
        LoginResponse response = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","password":"%s"}""".formatted(username, password))
                .exchange().expectStatus().isOk()
                .expectBody(LoginResponse.class).returnResult().getResponseBody();
        return response.accessToken();
    }
}
