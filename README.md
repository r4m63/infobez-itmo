# infobez-itmo — защищённый REST API

Spring Boot 4.1 (Java 25), PostgreSQL 18, Flyway, Spring Security + JWT (HS256).

## Запуск

```bash
cp .env.example .env          # заполнить своими значениями
docker compose up -d          # поднять PostgreSQL
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Профиль `dev` создаёт демо-пользователей `admin` / `user` с паролями из
`DEMO_ADMIN_PASSWORD` / `DEMO_USER_PASSWORD`. Без этого профиля пользователей нужно
заводить самостоятельно.

Тесты (`./mvnw verify`) поднимают PostgreSQL через Testcontainers, Docker должен быть запущен.

## Методы API

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| `POST` | `/auth/login` | публичный | Аутентификация по логину/паролю, возвращает JWT |
| `GET` | `/api/data` | JWT | Список постов с пагинацией (`page`, `size`) и поиском (`query`) |
| `POST` | `/api/posts` | JWT | Создание поста от имени владельца токена |

### Примеры

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"..."}' | jq -r .accessToken)

curl -s localhost:8080/api/data -H "Authorization: Bearer $TOKEN"

curl -s -X POST localhost:8080/api/posts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Заголовок","content":"Текст"}'
```

## Базовые меры защиты

### SQL-инъекции
Конкатенации SQL нет нигде. Доступ к данным — только через Spring Data JPA / Hibernate:
производные методы репозитория и JPQL с именованными параметрами
([PostRepository.search](src/main/java/com/itmo/infobez/post/PostRepository.java) — значение
подставляется как `:query`, Hibernate превращает его в `PreparedStatement`-параметр).
Имя поля сортировки задано в коде и не приходит от пользователя.

### XSS
Двухуровневая защита в [HtmlSanitizer](src/main/java/com/itmo/infobez/web/HtmlSanitizer.java):
- **на входе** — OWASP Java HTML Sanitizer с политикой без единого разрешённого тега:
  разметка вырезается до записи в БД;
- **на выходе** — `HtmlUtils.htmlEscape` для всех строковых полей ответа.

Дополнительно: ответы отдаются как `application/json`, включены заголовки
`X-Content-Type-Options: nosniff` и `Content-Security-Policy: default-src 'none'`.

### Broken Authentication
- JWT (HS256) выдаётся при успешном входе в
  [TokenService](src/main/java/com/itmo/infobez/security/TokenService.java); TTL 15 минут,
  claim `sub` — имя пользователя, `scope` — роли.
- Проверку токена на всех защищённых эндпоинтах выполняет middleware Spring Security —
  `BearerTokenAuthenticationFilter`, подключённый через `oauth2ResourceServer(...jwt(...))`
  в [SecurityConfig](src/main/java/com/itmo/infobez/config/SecurityConfig.java). Правило
  `anyRequest().authenticated()` означает, что фильтр защищает всё, кроме явного белого списка.
- Пароли хранятся только как **bcrypt**-хеши (cost 12), в открытом виде не хранятся и не логируются.
- Подбор паролей ограничен
  [LoginAttemptService](src/main/java/com/itmo/infobez/security/LoginAttemptService.java):
  5 неудачных попыток → блокировка логина на 15 минут (HTTP 429).

## Соответствие OWASP Top 10

- **A01 Broken Access Control** — всё, кроме `/auth/login` и `/actuator/health`, требует
  валидный JWT; автор поста берётся из `sub` токена, а не из тела запроса.
- **A02 Cryptographic Failures** — пароли хранятся как bcrypt (cost 12); JWT подписан HS256,
  секрет только из переменной окружения и минимум 32 символа.
- **A03 Injection** — доступ к БД только через JPA/подготовленные запросы, входные данные
  проверяются Bean Validation, HTML вырезается санитайзером (XSS).
- **A05 Security Misconfiguration** — stateless-сессии, `ddl-auto: validate` (схема — Flyway),
  заголовки CSP/HSTS/Referrer-Policy, actuator ограничен `health` без деталей.
- **A07 Identification and Authentication Failures** — одинаковый ответ 401 для неизвестного
  логина и неверного пароля (`hideUserNotFoundExceptions`), TTL токена 15 минут.
- **A09 Security Logging Failures** — неудачные входы логируются, пароль не попадает в логи
  (`LoginRequest.toString`), стектрейсы и сообщения ошибок наружу не отдаются.
