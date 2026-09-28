#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"

expect_status() {
    local expected="$1"
    shift
    local actual
    actual=$(curl -sS -o /dev/null -w '%{http_code}' "$@")
    if [ "$actual" != "$expected" ]; then
        echo "Ожидался HTTP $expected, получен $actual" >&2
        exit 1
    fi
    echo "HTTP $actual — проверка пройдена"
}

expect_status 401 "$API_BASE_URL/api/data"
expect_status 401 -H 'Authorization: Bearer invalid-token' "$API_BASE_URL/api/data"
expect_status 401 -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"wrong"}' "$API_BASE_URL/auth/login"
expect_status 401 -H 'Content-Type: application/json' \
    -d '{"title":"Без токена","content":"Тест"}' "$API_BASE_URL/api/posts"

access_token=$(get_token)
curl -fsSG --data-urlencode "query=' OR '1'='1" \
    -H "Authorization: Bearer $access_token" "$API_BASE_URL/api/data" |
    python3 -c 'import json,sys; assert json.load(sys.stdin)["total"] == 0; print("SQLi: пустой результат — проверка пройдена")'

curl -fsS -H "Authorization: Bearer $access_token" \
    -H 'Content-Type: application/json' \
    -d '{"title":"<script>alert(1)</script>","content":"<img src=x onerror=alert(1)>"}' \
    "$API_BASE_URL/api/posts" |
    python3 -c 'import json,sys; p=json.load(sys.stdin); assert p["title"] == "&lt;script&gt;alert(1)&lt;/script&gt;"; assert p["content"] == "&lt;img src=x onerror=alert(1)&gt;"; print("XSS: HTML экранирован — проверка пройдена")'
