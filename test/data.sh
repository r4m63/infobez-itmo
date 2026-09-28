#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"

access_token=$(get_token)
if [ "$#" -gt 0 ]; then
    curl -fsSG --data-urlencode "query=$1" \
        -H "Authorization: Bearer $access_token" "$API_BASE_URL/api/data" | python3 -m json.tool
else
    curl -fsS -H "Authorization: Bearer $access_token" \
        "$API_BASE_URL/api/data" | python3 -m json.tool
fi
