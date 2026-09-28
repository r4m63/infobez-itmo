#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
API_USERNAME="${API_USERNAME:-admin}"
API_PASSWORD="${API_PASSWORD:-admin-password}"

login() {
    local body
    body=$(python3 -c 'import json,sys; print(json.dumps({"username":sys.argv[1],"password":sys.argv[2]}))' "$API_USERNAME" "$API_PASSWORD")
    curl -fsS -H 'Content-Type: application/json' -d "$body" "$API_BASE_URL/auth/login"
}

get_token() {
    login | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'
}
