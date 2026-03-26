#!/usr/bin/env bash
set -euo pipefail

NODE1_BASE_URL="http://localhost:8088"
NODE2_BASE_URL="http://localhost:8091"
BEARER_TOKEN="demo-receiver-token"
SIGNATURE_SECRET="demo-receiver-secret"
SIGNATURE_ALGORITHM="HmacSHA256"
ADMIN_TOKEN="demo-admin-token"

while [[ $# -gt 0 ]]; do
  case $1 in
    --node1-base-url) NODE1_BASE_URL="$2"; shift 2 ;;
    --node2-base-url) NODE2_BASE_URL="$2"; shift 2 ;;
    --bearer-token) BEARER_TOKEN="$2"; shift 2 ;;
    --signature-secret) SIGNATURE_SECRET="$2"; shift 2 ;;
    --signature-algorithm) SIGNATURE_ALGORITHM="$2"; shift 2 ;;
    --admin-token) ADMIN_TOKEN="$2"; shift 2 ;;
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
import hmac, hashlib
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

echo "Checking instance metadata..."
curl -fsSL "$NODE1_BASE_URL/demo-tenants/current" | jq .
curl -fsSL "$NODE2_BASE_URL/demo-tenants/current" | jq .

echo "Posting signed alert to postgres/redis node 1..."
curl -fsSL "${receiver_headers[@]}" -H "Content-Type: application/json" -d "$payload" "$NODE1_BASE_URL/demo-alert-receiver" | jq .

echo "Replaying the same signed alert against postgres/redis node 2. Expecting replay protection..."
status=$(
  curl -sS -o /dev/null \
    "${receiver_headers[@]}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    -w "%{http_code}" \
    "$NODE2_BASE_URL/demo-alert-receiver"
)
if [[ "$status" -ne 409 ]]; then
  echo "Expected node 2 to reject the replayed nonce with HTTP 409, received $status" >&2
  exit 1
fi
echo "Node 2 rejected the replayed nonce with HTTP 409 as expected."

echo "Fetching observability summaries..."
node1_observability=$(curl -fsSL "${admin_headers[@]}" "$NODE1_BASE_URL/demo-tenants/observability")
node2_observability=$(curl -fsSL "${admin_headers[@]}" "$NODE2_BASE_URL/demo-tenants/observability")
echo "$node1_observability" | jq .
echo "$node2_observability" | jq .

node1_backend=$(echo "$node1_observability" | jq -r '.receiverReplayStore.backend')
node2_backend=$(echo "$node2_observability" | jq -r '.receiverReplayStore.backend')
if [[ "$node1_backend" != "REDIS" || "$node2_backend" != "REDIS" ]]; then
  echo "Expected both nodes to expose receiverReplayStore.backend=REDIS" >&2
  exit 1
fi

node1_audit=$(echo "$node1_observability" | jq -r '.auditRepositoryType')
node2_audit=$(echo "$node2_observability" | jq -r '.auditRepositoryType')
if [[ "$node1_audit" != "JDBC" || "$node2_audit" != "JDBC" ]]; then
  echo "Expected both nodes to use JDBC audit repositories" >&2
  exit 1
fi
