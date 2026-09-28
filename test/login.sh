#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"

login | python3 -m json.tool
