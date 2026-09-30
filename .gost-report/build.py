"""Отчёт по работе 1. Сборка: python3 ~/.claude/skills/gost-report/scripts/ensure_env.py .gost-report/build.py"""
import os

# Глобальный конфиг gost-report содержит заглушки преподавателя; все данные титула заданы ниже.
os.environ["GOST_REPORT_CONFIG"] = os.devnull

from gost_report import Report, TitleConfig, paths

REPO = "https://github.com/r4m63/infobez-itmo"
RUN_URL = f"{REPO}/actions/runs/36620490824/job/109584335296"
SHOTS = paths().root / "docs" / "screenshots"

r = Report(TitleConfig(
    work_type="Работа",
    work_number="1",
    topic="Разработка защищенного REST API с интеграцией в CI/CD",
    faculty="Дисциплина «Информационная безопасность»",
    university_full=("Федеральное государственное автономное "
                     "образовательное учреждение высшего образования"),
    student_name="Таджеддинов Р. Э.",
    student_group="P3408",
    year="2026",
))

r.toc()

# ---------------------------------------------------------------- Введение
r.h1("Введение")
r.text("Цель работы: получить практический опыт разработки безопасного "
       "backend-приложения с автоматической проверкой кода на уязвимости, "
       "освоить защиту от рисков OWASP Top 10 и встроить инструменты "
       "безопасности в процесс разработки.")
r.text("Задачи:")
r.numbered([
    "Создать проект на Java (Maven), связать его с публичным репозиторием GitHub.",
    "Реализовать три метода API: вход POST /auth/login, получение данных "
    "GET /api/data и собственный метод POST /api/posts.",
    "Защитить API от SQL-инъекций, XSS и ошибок аутентификации: JWT, "
    "middleware проверки токена, хэширование паролей bcrypt.",
    "Настроить GitHub Actions с SAST (SpotBugs) и SCA (OWASP Dependency-Check), "
    "запускаемыми на каждый push и pull request.",
    "Проверить работу API через curl и зафиксировать отчёты сканеров.",
])

# ---------------------------------------------------------------- Репозиторий
r.h1("Ссылка на репозиторий")
r.text(f"Код проекта размещён в публичном репозитории GitHub: {REPO}.")
r.text("Файл README.md в корне репозитория содержит описание API, мер защиты, "
       "pipeline и скриншоты отчётов. Ниже приведено его содержание.")

# ---------------------------------------------------------------- Проект и API
r.h1("Описание проекта и API")
r.h2("Стек")
t_stack = r.table([
    ["Компонент", "Технология"],
    ["Язык и сборка", "Java 25, Maven (wrapper ./mvnw)"],
    ["Фреймворк", "Spring Boot 4.1.1: Web, Security, OAuth2 Resource Server, Data JPA, Validation"],
    ["База данных", "H2 в памяти, доступ через Hibernate (JPA)"],
    ["Токены", "JWT, алгоритм HS256 (Nimbus JOSE)"],
    ["Хэширование паролей", "bcrypt, cost 12"],
    ["SAST", "SpotBugs 4.9.6 и Find Security Bugs 1.14.0"],
    ["SCA", "OWASP Dependency-Check 12.2.2"],
], caption="Стек проекта")
r.text(f"Используемые технологии перечислены {r.ref.in_table(t_stack)}. "
       "Логика API находится в классе ApiController, правила доступа и "
       "проверка JWT в SecurityConfig, хранение постов в сущности Post и "
       "репозитории PostRepository.")

r.h2("Запуск")
r.code("./mvnw spring-boot:run")
r.text("Приложение слушает порт 8080. Данные H2 хранятся в памяти и "
       "сбрасываются при перезапуске. Учебные пользователи: admin с паролем "
       "admin-password и user с паролем user-password. В application.yaml "
       "записаны только bcrypt-хэши этих паролей.")

r.h2("Эндпоинты")
t_api = r.table([
    ["Метод", "Путь", "Доступ", "Назначение"],
    ["POST", "/auth/login", "публичный", "Проверяет логин и пароль, возвращает JWT"],
    ["GET", "/api/data", "Bearer JWT", "Список постов, параметр query ищет по заголовку"],
    ["POST", "/api/posts", "Bearer JWT", "Создаёт пост, автор берётся из токена"],
], caption="Методы API")
r.text(f"{r.ref.in_table(t_api, cap=True)} перечислены методы API. "
       "Спецификация OpenAPI лежит в файле openapi.yaml, её можно импортировать "
       "в Postman.")

r.h3("POST /auth/login")
r.code("curl -X POST localhost:8080/auth/login \\\n"
       "  -H 'Content-Type: application/json' \\\n"
       "  -d '{\"username\":\"admin\",\"password\":\"admin-password\"}'\n\n"
       "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\",\"expiresIn\":900}")
r.text("При неверном логине или пароле сервер отвечает 401 с одинаковым "
       "текстом «Invalid username or password». Пустые или слишком длинные "
       "поля дают 400.")

r.h3("GET /api/data")
r.code("curl localhost:8080/api/data -H \"Authorization: Bearer $TOKEN\"\n\n"
       "{\"items\":[{\"id\":2,\"title\":\"Второй пост\",\"content\":\"...\",\n"
       "  \"author\":\"user\",\"createdAt\":\"2026-09-30T17:07:34Z\"}, ...],\"total\":2}")
r.text("Без токена или с недействительным токеном ответ 401.")

r.h3("POST /api/posts")
r.code("curl -X POST localhost:8080/api/posts \\\n"
       "  -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' \\\n"
       "  -d '{\"title\":\"Заголовок\",\"content\":\"Текст\"}'")
r.text("Ответ 201 Created с созданным постом. Поле author заполняется из "
       "claim sub проверенного токена, поэтому подставить чужое имя через "
       "JSON нельзя.")

# ---------------------------------------------------------------- Защита
r.h1("Меры защиты")
r.h2("Защита от SQL-инъекций")
r.text("SQL вручную не собирается: все обращения к БД идут через Spring Data "
       "JPA и Hibernate. Поиск по заголовку написан на JPQL с именованным "
       "параметром:")
r.code("@Query(\"SELECT p FROM Post p WHERE LOWER(p.title) \"\n"
       "     + \"LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.createdAt DESC\")\n"
       "List<Post> searchByTitle(@Param(\"query\") String query);")
r.text("Hibernate превращает :query в placeholder prepared statement, значение "
       "передаётся драйверу отдельно от текста запроса. Нагрузка ' OR '1'='1 "
       "ищется как обычная строка и возвращает пустой список. Длина параметра "
       "ограничена 100 символами.")

r.h2("Защита от XSS")
r.text("Текстовые поля поста (title, content, author) перед отправкой клиенту "
       "проходят через HtmlUtils.htmlEscape из Spring:")
r.code("private static PostDto escape(Post post) {\n"
       "    return new PostDto(post.getId(), HtmlUtils.htmlEscape(post.getTitle()),\n"
       "            HtmlUtils.htmlEscape(post.getContent()),\n"
       "            HtmlUtils.htmlEscape(post.getAuthor()), post.getCreatedAt());\n"
       "}")
r.text("Строка <script>alert(1)</script> возвращается как "
       "&lt;script&gt;alert(1)&lt;/script&gt;. Дополнительно все ответы "
       "содержат заголовки X-Content-Type-Options: nosniff и "
       "Content-Security-Policy: default-src 'none'; frame-ancestors 'none', "
       "поэтому браузер не выполнит ответ API как HTML или скрипт.")

r.h2("Аутентификация")
r.bullet([
    "Пароли хранятся только в виде bcrypt-хэшей с солью, cost 12 "
    "(BCryptPasswordEncoder(12)). Открытых паролей в коде и конфигурации нет.",
    "После успешной проверки пароля /auth/login выпускает JWT с алгоритмом "
    "HS256 и claims iss, sub, iat, exp. Срок жизни токена 15 минут.",
    "Ключ подписи длиной 256 бит генерируется SecureRandom при старте "
    "приложения и не хранится в репозитории.",
    "Middleware проверки токена: в SecurityConfig подключён фильтр "
    "BearerTokenAuthenticationFilter (OAuth2 Resource Server) с "
    "NimbusJwtDecoder. Он проверяет подпись, алгоритм, срок действия и "
    "издателя; при любой ошибке запрос получает 401.",
    "Без токена открыт только /auth/login, остальные пути требуют "
    "аутентификации (anyRequest().authenticated()).",
    "Для несуществующего логина bcrypt всё равно выполняется, текст ответа "
    "одинаковый, поэтому по времени и содержанию ответа нельзя узнать, "
    "существует ли пользователь.",
    "API не использует сессии и cookie (SessionCreationPolicy.STATELESS), "
    "поэтому CSRF-защита не требуется и отключена.",
])
r.code("http.csrf(AbstractHttpConfigurer::disable)\n"
       "    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n"
       "    .authorizeHttpRequests(auth -> auth\n"
       "        .requestMatchers(\"/auth/login\").permitAll()\n"
       "        .anyRequest().authenticated())\n"
       "    .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)))")

r.h2("Валидация входных данных")
r.text("Все входные DTO проверяются Bean Validation (@NotBlank, @Size). Ошибки "
       "возвращаются в формате ProblemDetail без стектрейсов. Метод "
       "toString у LoginRequest маскирует пароль, чтобы он не попал в логи.")

# ---------------------------------------------------------------- CI/CD
r.h1("Настройка CI/CD pipeline")
r.text("Pipeline описан в файле .github/workflows/ci.yml. Он запускается на "
       "каждый push и pull_request, а также вручную через workflow_dispatch:")
r.code("on:\n  push:\n  pull_request:\n  workflow_dispatch:")
t_ci = r.table([
    ["Job", "Инструмент", "Что делает", "Когда падает"],
    ["Build", "Maven", "Сборка ./mvnw package", "Ошибка компиляции или сборки"],
    ["SAST. SpotBugs code analysis", "SpotBugs и Find Security Bugs",
     "Анализ байткода (effort Max, threshold Medium), отчёт XML и HTML",
     "Найдено хотя бы одно замечание"],
    ["SCA. OWASP Dependency-Check", "OWASP Dependency-Check",
     "Сверка прямых и транзитивных зависимостей с базой NVD, отчёт HTML и JSON",
     "Уязвимость с CVSS не ниже 7 или отчёт не создан"],
], caption="Задачи pipeline")
r.text(f"Три задачи выполняются параллельно, их назначение описано "
       f"{r.ref.in_table(t_ci)}. Отчёты сохраняются как артефакты "
       "spotbugs-report и dependency-check-report, краткий итог выводится в "
       "Job Summary. Dependency-Check получает ключ NVD_API_KEY из секрета "
       "GitHub Actions, значение ключа в коде не хранится.")
r.code("- name: Analyze code with SpotBugs and Find Security Bugs\n"
       "  run: ./mvnw $MAVEN_ARGS -DskipTests compile spotbugs:spotbugs spotbugs:check\n\n"
       "- name: Scan dependencies with OWASP Dependency-Check\n"
       "  env:\n"
       "    NVD_API_KEY: ${{ secrets.NVD_API_KEY }}\n"
       "  run: ./mvnw $MAVEN_ARGS -DskipTests dependency-check:check")

# ---------------------------------------------------------------- Отчёты
r.h1("Отчёты SAST и SCA")
r.text(f"Скриншоты сделаны для запуска #54 (коммит d3a069c): {RUN_URL}. "
       "Отчёты SpotBugs и Dependency-Check взяты из артефактов этого запуска.")
f_run = r.figure(str(SHOTS / "01-ci-success.png"),
                 "Успешный запуск pipeline в GitHub Actions")
f_list = r.figure(str(SHOTS / "02-actions-list.png"),
                  "История запусков workflow Build and Security Checks")
r.text(f"{r.ref.on_figure(f_run, cap=True)} видно, что все три задачи "
       "завершились успешно, а в Job Summary задачи SAST указано 0 найденных "
       f"проблем. {r.ref.on_figure(f_list, cap=True)} показана "
       "история запусков: pipeline срабатывает на каждый push.")
f_sb = r.figure(str(SHOTS / "04-spotbugs-report.png"),
                "Отчёт SpotBugs и Find Security Bugs из CI")
r.text(f"{r.ref.on_figure(f_sb, cap=True)} приведён отчёт SAST: проанализирован "
       "171 строка кода в 9 классах, замечаний высокого и среднего приоритета нет.")
f_dc = r.figure(str(SHOTS / "05-dependency-check-report.png"),
                "Отчёт OWASP Dependency-Check из CI")
r.text(f"{r.ref.on_figure(f_dc, cap=True)} приведён отчёт SCA: проверено 95 "
       "зависимостей (49 уникальных), уязвимых зависимостей 0.")
r.text("В ранних версиях pipeline сканер находил уязвимые версии зависимостей, "
       "в том числе встроенного Tomcat. Версии были обновлены (свойство "
       "tomcat.version 11.0.25 в pom.xml), после чего проверки проходят.")

# ---------------------------------------------------------------- Тестирование
r.h1("Тестирование API")
r.text("API проверено вручную через curl, полный протокол находится в файле "
       "docs/curl-session.txt репозитория.")
t_test = r.table([
    ["Сценарий", "Ожидаемый ответ", "Фактический ответ"],
    ["GET /api/data без токена", "401", "401"],
    ["Вход с неверным паролем", "401", "401"],
    ["Вход admin с верным паролем", "200 и JWT", "200 и JWT, expiresIn 900"],
    ["GET /api/data с токеном", "200, список постов", "200, 2 поста"],
    ["POST /api/posts с тегом script в заголовке", "201, HTML экранирован",
     "201, &lt;script&gt;..."],
    ["GET /api/data?query=' OR '1'='1", "Пустой список без ошибки", "{\"items\":[],\"total\":0}"],
    ["Токен с изменённой подписью", "401", "401"],
    ["POST /api/posts без токена", "401", "401"],
], caption="Результаты ручной проверки API")
r.text(f"Все сценарии {r.ref.in_table(t_test)} дали ожидаемый результат: "
       "аутентификация работает, доступ без токена запрещён, XSS- и "
       "SQLi-нагрузки обезврежены.")
r.code("$ curl -i localhost:8080/api/data\n"
       "HTTP/1.1 401\n\n"
       "$ curl -G localhost:8080/api/data --data-urlencode \"query=' OR '1'='1\" \\\n"
       "       -H \"Authorization: Bearer $TOKEN\"\n"
       "{\"items\":[],\"total\":0}\n\n"
       "$ curl -X POST localhost:8080/api/posts -H \"Authorization: Bearer $TOKEN\" \\\n"
       "       -H 'Content-Type: application/json' \\\n"
       "       -d '{\"title\":\"<script>alert(1)</script>Тест\",\"content\":\"<img src=x onerror=alert(1)>текст\"}'\n"
       "{\"id\":3,\"title\":\"&lt;script&gt;alert(1)&lt;/script&gt;Тест\",\n"
       " \"content\":\"&lt;img src=x onerror=alert(1)&gt;текст\",\"author\":\"admin\",...}")

# ---------------------------------------------------------------- Pipeline link
r.h1("Ссылка на последний успешный запуск pipeline")
r.text(f"Запуск #54 workflow Build and Security Checks: {RUN_URL}.")

# ---------------------------------------------------------------- Вопросы
r.h1("Ответы на контрольные вопросы")
r.task("Вопрос 1. Почему хэширование пароля с помощью bcrypt предпочтительнее SHA-256?")
r.text("SHA-256 создан быстрым: видеокарта перебирает миллиарды хэшей в "
       "секунду, поэтому украденную базу легко атаковать словарём. bcrypt "
       "намеренно медленный, его стоимость настраивается (в проекте 2^12 "
       "раундов) и её можно повышать вслед за ростом мощности железа. Соль "
       "генерируется для каждого пароля и хранится внутри хэша, поэтому "
       "радужные таблицы бесполезны, а одинаковые пароли дают разные хэши.")
r.task("Вопрос 2. В чём основная разница между SAST и DAST?")
r.text("SAST анализирует исходный код или байткод без запуска приложения "
       "(белый ящик). Он находит проблему рано и указывает строку, но даёт "
       "ложные срабатывания и не видит ошибок конфигурации окружения. DAST "
       "атакует работающее приложение по HTTP (чёрный ящик): видит реальное "
       "поведение и настройки сервера, но не показывает место в коде и "
       "требует развёрнутого стенда.")
r.task("Вопрос 3. Как работает JWT, что в нём содержится и как сервер проверяет подлинность?")
r.text("Токен состоит из трёх частей header.payload.signature, закодированных "
       "Base64URL. Header хранит алгоритм подписи (HS256), payload хранит "
       "claims: в проекте это издатель iss, пользователь sub, время выпуска "
       "iat и истечения exp. Подпись вычисляется как HMAC-SHA256 от "
       "header.payload на секретном ключе сервера. При запросе сервер "
       "пересчитывает подпись своим ключом, сравнивает её с присланной, затем "
       "проверяет exp и iss. Payload не зашифрован, поэтому секреты в него "
       "класть нельзя.")
r.task("Вопрос 4. Какие риски возникают без аудита сторонних зависимостей?")
r.text("Большая часть кода приложения приходит из сторонних библиотек, в том "
       "числе транзитивных. Без аудита в продакшен попадают компоненты с "
       "известными CVE (например, Log4Shell или Spring4Shell), для которых "
       "уже есть готовые эксплойты. Растёт риск атак на цепочку поставок и "
       "лицензионных проблем. SCA в CI находит такие зависимости при каждом "
       "коммите, пока их легко обновить.")

# ---------------------------------------------------------------- Заключение
r.h1("Заключение")
r.numbered([
    "Реализован REST API на Spring Boot с тремя методами: вход, получение "
    "данных и создание поста.",
    "SQL-инъекции исключены параметризованными JPQL-запросами Hibernate.",
    "XSS предотвращён экранированием HtmlUtils.htmlEscape и заголовками CSP и nosniff.",
    "Аутентификация построена на JWT HS256 с проверкой в фильтре Spring "
    "Security, пароли хранятся как bcrypt-хэши.",
    "GitHub Actions на каждый push и pull request запускает сборку, SAST "
    "(SpotBugs) и SCA (OWASP Dependency-Check); последний запуск прошёл "
    "без замечаний и уязвимостей.",
])

r.save()
