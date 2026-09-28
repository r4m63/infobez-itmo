#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"

title="${1:-Проверочный пост}"
content="${2:-Создан curl-скриптом}"
body=$(python3 -c 'import json,sys; print(json.dumps({"title":sys.argv[1],"content":sys.argv[2]}))' "$title" "$content")
access_token=$(get_token)
curl -fsS -H "Authorization: Bearer $access_token" \
    -H 'Content-Type: application/json' -d "$body" \
    "$API_BASE_URL/api/posts" | python3 -m json.tool
