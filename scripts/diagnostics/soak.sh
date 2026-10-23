#!/usr/bin/env bash
# Sends a steady trickle of requests through CloudFront and reports every slow one together with
# CloudFront's request id (x-amz-cf-id), which is the key to find that exact request in the
# CloudFront real-time log and in AWS support.
#
#   scripts/diagnostics/soak.sh <base url> [requests] [pause seconds] [slow threshold seconds]
#   scripts/diagnostics/soak.sh "$URL" 300 2 1
#
# Needs no token: the API path used answers 401 straight from the app, so it exercises
# CloudFront -> load balancer -> app without Redis or the database. Output goes to stdout and to
# soak-<timestamp>.txt in the current directory.
set -u

URL="${1:?usage: soak.sh <base url> [requests] [pause] [slow threshold]}"
COUNT="${2:-200}"
PAUSE="${3:-2}"
SLOW="${4:-1}"
OUT="soak-$(date +%Y%m%d-%H%M%S).txt"
HEADERS="$(mktemp)"
trap 'rm -f "$HEADERS"' EXIT

# "/" is served from S3 and never reaches the app, so it separates a CloudFront/client problem from
# an API-origin one. The other path is rejected by the app (401) without touching Redis or the database.
PATHS=("/" "/api/players/search?q=a")

slow=0
total=0
for ((i = 1; i <= COUNT; i++)); do
  for p in "${PATHS[@]}"; do
    line=$(curl -s -o /dev/null -D "$HEADERS" \
      -w "%{http_code} dns %{time_namelookup} conn %{time_connect} tls %{time_appconnect} ttfb %{time_starttransfer} total %{time_total}" \
      "$URL$p")
    total=$((total + 1))
    time_total="${line##* }"
    stamp="$(date +%T)"
    if awk -v t="$time_total" -v s="$SLOW" 'BEGIN { exit !(t + 0 > s + 0) }'; then
      slow=$((slow + 1))
      cf_id=$(grep -i '^x-amz-cf-id:' "$HEADERS" | tr -d '\r' | awk '{print $2}')
      pop=$(grep -i '^x-amz-cf-pop:' "$HEADERS" | tr -d '\r' | awk '{print $2}')
      echo "$stamp SLOW $p $line cf-id=$cf_id pop=$pop" | tee -a "$OUT"
    else
      echo "$stamp ok   $p $line" >> "$OUT"
    fi
  done
  sleep "$PAUSE"
done

echo "done: $slow slow of $total requests (over ${SLOW}s). Full record: $OUT" | tee -a "$OUT"
