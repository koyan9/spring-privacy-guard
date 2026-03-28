#!/usr/bin/env bash
set -euo pipefail

NODE1_BASE_URL="http://localhost:8088"
NODE2_BASE_URL="http://localhost:8089"
RECEIVER_PATH="/demo-alert-receiver"
BEARER_TOKEN="demo-receiver-token"
SIGNATURE_SECRET="demo-receiver-secret"
SIGNATURE_ALGORITHM="HmacSHA256"
ADMIN_TOKEN="demo-admin-token"
TENANT_ID=""
EXPECTED_REPLAY_NAMESPACE=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --node1-base-url) NODE1_BASE_URL="$2"; shift 2 ;;
    --node2-base-url) NODE2_BASE_URL="$2"; shift 2 ;;
    --receiver-path) RECEIVER_PATH="$2"; shift 2 ;;
    --bearer-token) BEARER_TOKEN="$2"; shift 2 ;;
    --signature-secret) SIGNATURE_SECRET="$2"; shift 2 ;;
    --signature-algorithm) SIGNATURE_ALGORITHM="$2"; shift 2 ;;
    --admin-token) ADMIN_TOKEN="$2"; shift 2 ;;
    --tenant-id) TENANT_ID="$2"; shift 2 ;;
    --expected-replay-namespace) EXPECTED_REPLAY_NAMESPACE="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

if [[ "$SIGNATURE_ALGORITHM" != "HmacSHA256" ]]; then
  echo "Only HmacSHA256 is supported by this helper script. Received: $SIGNATURE_ALGORITHM" >&2
  exit 1
fi

payload=$(
  cat <<'EOF'
{
  "state": "WARNING",
  "total": 2,
  "warningThreshold": 1,
  "downThreshold": 5,
  "byAction": {
    "READ": 2
  }
}
EOF
)

timestamp=$(date -u +%s)
nonce=$(uuidgen)
signature=$(
  python - <<PY
import hmac, hashlib, sys
secret = "${SIGNATURE_SECRET}"
data = f"${timestamp}.${nonce}.$payload"
print(hmac.new(secret.encode(), data.encode(), hashlib.sha256).hexdigest())
PY
)

receiver_headers=(
  -H "Authorization: Bearer $BEARER_TOKEN"
  -H "X-Privacy-Alert-Timestamp: $timestamp"
  -H "X-Privacy-Alert-Nonce: $nonce"
  -H "X-Privacy-Alert-Signature: $signature"
)

admin_headers=(-H "X-Demo-Admin-Token: $ADMIN_TOKEN")
if [[ -n "$TENANT_ID" ]]; then
  admin_headers+=(-H "X-Privacy-Tenant: $TENANT_ID")
fi

echo "Checking instance metadata..."
curl -fsSL "$NODE1_BASE_URL/demo-tenants/current" | jq .
curl -fsSL "$NODE2_BASE_URL/demo-tenants/current" | jq .

echo "Posting signed alert to node 1..."
curl -fsSL "${receiver_headers[@]}" -H "Content-Type: application/json" -d "$payload" "$NODE1_BASE_URL$RECEIVER_PATH" | jq .

echo "Checking replay-store visibility from node 2..."
replay_store=$(curl -fsSL "${admin_headers[@]}" "$NODE2_BASE_URL/demo-alert-receiver/replay-store?limit=20&offset=0")
echo "$replay_store" | jq .

if [[ -n "$EXPECTED_REPLAY_NAMESPACE" ]]; then
  expected_storage_key="${EXPECTED_REPLAY_NAMESPACE}:${nonce}"
  if ! echo "$replay_store" | jq -e --arg key "$expected_storage_key" '.entries[].storageKey | select(. == $key)' >/dev/null; then
    echo "Expected replay-store to contain storage key $expected_storage_key" >&2
    exit 1
  fi
fi

echo "Replaying the same signed alert against node 2. Expecting replay protection..."
status=$(
  curl -sS -o /dev/null \
    "${receiver_headers[@]}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    -w "%{http_code}" \
    "$NODE2_BASE_URL$RECEIVER_PATH"
)
if [[ "$status" -ne 409 ]]; then
  echo "Expected node 2 to reject the replayed nonce with HTTP 409, received $status" >&2
  exit 1
fi
echo "Node 2 rejected the replayed nonce with HTTP 409 as expected."

echo "Fetching observability summaries..."
curl -fsSL "${admin_headers[@]}" "$NODE1_BASE_URL/demo-tenants/observability" | jq .
curl -fsSL "${admin_headers[@]}" "$NODE2_BASE_URL/demo-tenants/observability" | jq .
