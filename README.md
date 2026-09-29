# Работа 1: защищённый REST API

Учебное приложение на Java 25 и Spring Boot 4.1.1. Вся логика API находится в
[ApiController.java](src/main/java/com/itmo/infobezitmo/ApiController.java).
Для хранения есть одна [JPA-сущность Post](src/main/java/com/itmo/infobezitmo/Post.java)
и один [репозиторий](src/main/java/com/itmo/infobezitmo/PostRepository.java).
Hibernate работает с H2 в памяти; данные сбрасываются при перезапуске.
Правила доступа и проверка JWT находятся в
[SecurityConfig.java](src/main/java/com/itmo/infobezitmo/SecurityConfig.java).

- [Публичный репозиторий](https://github.com/r4m63/infobez-itmo)
- [GitHub Actions](https://github.com/r4m63/infobez-itmo/actions/workflows/ci.yml)
- [Последний проверенный успешный запуск CI](https://github.com/r4m63/infobez-itmo/actions/runs/36596932895)

## Запуск

```bash
./mvnw spring-boot:run
```

Java 25 необходима для сборки. Проверки локально: `./mvnw -DskipTests package` и
`./mvnw -DskipTests compile spotbugs:spotbugs spotbugs:check`.
Учебные пользователи: `admin` / `admin-password` и `user` / `user-password`.
В `application.yaml` записаны только bcrypt-хэши паролей (cost 12).
Эти публичные учётные данные предназначены только для локальной демонстрации.

## API

| Метод | Путь | Доступ | Назначение |
|---|---|---|---|
| POST | `/auth/login` | Публичный | Логин и пароль → JWT |
| GET | `/api/data` | Bearer JWT | Список постов; `?query=` ищет по заголовку |
| POST | `/api/posts` | Bearer JWT | Создать пост; автор берётся из токена |

Спецификация всех трёх методов: [openapi.yaml](openapi.yaml).
Чтобы проверить API через Postman, запустите приложение, импортируйте этот файл
через **Import → File** и вызовите `POST /auth/login`. Скопируйте
`accessToken` из ответа; для `GET /api/data` и `POST /api/posts` выберите
**Authorization → Bearer Token** и вставьте токен без префикса `Bearer`.

```bash
curl -i localhost:8080/api/data
# 401 Unauthorized без токена

curl -i -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-password"}'
# 200 OK: {"accessToken":"...","tokenType":"Bearer","expiresIn":900}

TOKEN='<accessToken из ответа>'
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/data
curl -X POST localhost:8080/api/posts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Заголовок","content":"Текст"}'
```

Ответ на создание поста имеет статус 201. Неверный пароль и недействительный JWT
дают 401. [Протокол ручной проверки curl](docs/curl-session.txt).

Команды для трёх запросов: [test/requests.txt](test/requests.txt). В защищённых
запросах замените `<accessToken>` токеном из ответа login.

## Защита

- **SQLi:** операции с H2 выполняются через Spring Data JPA / Hibernate.
  Поиск использует именованный параметр `:query` в JPQL, поэтому введённая
  строка не становится частью SQL-команды. Нагрузка `' OR '1'='1`
  возвращает пустой список.
- **XSS:** поля поста экранируются `HtmlUtils.htmlEscape` перед возвратом в JSON.
  Например, `<script>` становится `&lt;script&gt;`. Также включены
  `Content-Security-Policy` и `X-Content-Type-Options: nosniff`.
- **Аутентификация:** пароль проверяется по bcrypt-хэшу; при успехе выдаётся JWT,
  подписанный HS256. Случайный ключ создаётся при каждом запуске приложения;
  срок действия токена — 15 минут. После перезапуска ранее выданные токены
  становятся недействительными.
  Стандартный фильтр Spring Security проверяет подпись, срок и издателя токена.
  Только `/auth/login` открыт без токена. Автор нового поста берётся из
  проверенной учётной записи, а не из JSON запроса.
- **Валидация:** логин, пароль, заголовок и текст не могут быть пустыми; их длина
  ограничена. Ошибки возвращаются без стектрейсов. API не использует cookie и
  серверные сессии.

Для публичного развёртывания дополнительно понадобятся HTTPS, ограничение
частоты попыток входа и механизм отзыва токенов. Ручная проверка API через curl
описана выше.

## CI и отчёты

[Workflow](.github/workflows/ci.yml) запускается при каждом `push` и
`pull_request`. Его шаги:

| Проверка | Инструмент | Результат |
|---|---|---|
| Сборка | Maven | Ошибка job при ошибке сборки |
| SAST | SpotBugs + Find Security Bugs | Анализ Java-байткода, XML/HTML-отчёт |
| SCA | OWASP Dependency-Check | Проверка зависимостей на известные CVE, HTML/JSON-отчёт |

Dependency-Check запускается при каждом push и pull request. Отчёты
`target/dependency-check-report.html` и `target/dependency-check-report.json`
сохраняются в артефакте `dependency-check-report`. Job завершается ошибкой при
CVSS ≥ 7 или если сканер не смог создать отчёт. Первое обновление базы NVD
может занять много времени. В CI сканер получает `NVD_API_KEY` из секрета GitHub
Actions, чтобы быстрее загружать данные NVD; значение ключа не хранится в коде.

В [успешном запуске](https://github.com/r4m63/infobez-itmo/actions/runs/36596932895)
SpotBugs и Dependency-Check завершились успешно. Dependency-Check проверил
95 зависимостей (49 уникальных) и не нашёл известных уязвимостей. Дополнительный
анализатор Sonatype OSS Index был пропущен из-за отсутствия его учётных данных;
проверка по NVD выполнена. Первые три скриншота относятся к более раннему
запуску, последний — к указанному выше.

![Успешный запуск GitHub Actions](docs/screenshots/01-ci-success.png)
![Список запусков](docs/screenshots/02-actions-list.png)
![Отчёт SpotBugs без замечаний](docs/screenshots/04-spotbugs-report.png)
![Отчёт OWASP Dependency-Check: 0 найденных уязвимостей](docs/screenshots/05-dependency-check-report.png)

## Контрольные вопросы

1. **Почему bcrypt, а не SHA-256?** SHA-256 слишком быстр для хранения паролей:
   украденные хэши удобно перебирать. bcrypt использует случайную соль и
   настраиваемую стоимость вычисления (здесь cost 12).
2. **Чем SAST отличается от DAST?** SAST анализирует код или байткод без запуска
   приложения. DAST посылает запросы работающему приложению и изучает ответы.
3. **Как работает JWT?** Токен состоит из `header.payload.signature`. Здесь
   payload содержит издателя `iss`, пользователя `sub`, время выпуска `iat` и
   истечения `exp`. Сервер проверяет HMAC-подпись и ограничения времени и
   издателя. Payload закодирован, но не зашифрован.
4. **Зачем аудит зависимостей?** Даже безопасный собственный код может
   использовать библиотеку с известной CVE. SCA находит такие зависимости,
   включая косвенные, чтобы их можно было обновить или заменить.
