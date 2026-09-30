# Разработка защищенного REST API с интеграцией в CI/CD

REST API на Java 25 + Spring Boot 4.1.1 (Maven). Данные хранятся в H2 в памяти,
доступ к БД через Hibernate (JPA). Реализована защита от SQL-инъекций и XSS,
аутентификация по JWT с хэшированием паролей bcrypt. При каждом `push` и
`pull_request` GitHub Actions запускает SAST (SpotBugs) и SCA (OWASP Dependency-Check).

- Репозиторий: <https://github.com/r4m63/infobez-itmo>
- Последний успешный запуск pipeline: <https://github.com/r4m63/infobez-itmo/actions/runs/36620490824/job/109584335296>

## API

Запуск: `./mvnw spring-boot:run`, приложение слушает `http://localhost:8080`.
Учебные пользователи: `admin` / `admin-password` и `user` / `user-password`.

| Метод | Путь          | Доступ                        | Назначение                               |
|-------|---------------|-------------------------------|------------------------------------------|
| POST  | `/auth/login` | без токена                    | Проверяет логин и пароль, возвращает JWT |
| GET   | `/api/data`   | только аутентифицированные    | Список постов; `?query=` ищет по заголовку |
| POST  | `/api/posts`  | только аутентифицированные    | Создаёт пост                             |

### POST /auth/login

```bash
curl -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-password"}'
```

```json
{"accessToken":"eyJhbGciOiJIUzI1NiJ9...","tokenType":"Bearer","expiresIn":900}
```

Неверный логин или пароль: `401`.

### GET /api/data

```bash
curl localhost:8080/api/data -H "Authorization: Bearer $TOKEN"
```

```json
{"items":[{"id":2,"title":"Второй пост","content":"...","author":"user","createdAt":"..."}, ...],"total":2}
```

Без токена или с недействительным токеном: `401`.

### POST /api/posts

```bash
curl -X POST localhost:8080/api/posts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Заголовок","content":"Текст"}'
```

Ответ `201 Created` с созданным постом. Без токена: `401`.

## Меры защиты

### Защита от SQL-инъекций

SQL-запросы не собираются конкатенацией строк: все обращения к БД идут через ORM
Hibernate (Spring Data JPA). Поиск по заголовку использует JPQL с именованным
параметром ([PostRepository.java](src/main/java/com/itmo/infobezitmo/PostRepository.java)):

```java
@Query("SELECT p FROM Post p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.createdAt DESC")
List<Post> searchByTitle(@Param("query") String query);
```

Hibernate выполняет его как prepared statement: значение `:query` передаётся
в БД отдельно от текста запроса. Нагрузка `' OR '1'='1` ищется как обычная
строка и возвращает пустой список.

### Защита от XSS

Все пользовательские данные в ответах API экранируются встроенной функцией
Spring `HtmlUtils.htmlEscape` ([ApiController.java](src/main/java/com/itmo/infobezitmo/ApiController.java)):

```java
private static PostDto escape(Post post) {
    return new PostDto(post.getId(), HtmlUtils.htmlEscape(post.getTitle()),
            HtmlUtils.htmlEscape(post.getContent()), HtmlUtils.htmlEscape(post.getAuthor()), post.getCreatedAt());
}
```

Заголовок `<script>alert(1)</script>` возвращается как `&lt;script&gt;alert(1)&lt;/script&gt;`.

### Аутентификация

- **Хэширование паролей.** Пароли не хранятся в открытом виде: в
  `application.yaml` записаны только bcrypt-хэши (`BCryptPasswordEncoder`, cost 12).
  При входе `passwords.matches(...)` сравнивает введённый пароль с хэшем.
- **Выдача JWT.** При успешном входе `/auth/login` возвращает JWT, подписанный
  HS256, с полями `iss`, `sub` (логин), `iat`, `exp`. Срок жизни токена 15 минут.
- **Middleware проверки JWT.** В [SecurityConfig.java](src/main/java/com/itmo/infobezitmo/SecurityConfig.java)
  фильтр Spring Security (OAuth2 Resource Server) проверяет токен из заголовка
  `Authorization: Bearer` на всех защищённых эндпоинтах: подпись, срок действия
  и издателя. Без токена открыт только `/auth/login`, на остальные запросы без
  валидного токена возвращается `401`.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/login").permitAll()
        .anyRequest().authenticated())
.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)))
```

## Отчёты SAST/SCA

Pipeline [.github/workflows/ci.yml](.github/workflows/ci.yml) запускается при
каждом `push` и `pull_request`. SAST выполняет SpotBugs с плагином Find Security
Bugs, SCA выполняет OWASP Dependency-Check. Скриншоты относятся к
[запуску #54](https://github.com/r4m63/infobez-itmo/actions/runs/36620490824/job/109584335296).

**Успешный запуск pipeline, в Job Summary SpotBugs найдено 0 проблем.**

![Успешный запуск GitHub Actions](docs/screenshots/01-ci-success.png)

**SAST, SpotBugs + Find Security Bugs: 0 замечаний.**

![Отчёт SpotBugs](docs/screenshots/04-spotbugs-report.png)

**SCA, OWASP Dependency-Check: 95 зависимостей (49 уникальных), 0 уязвимостей.**

![Отчёт OWASP Dependency-Check](docs/screenshots/05-dependency-check-report.png)
