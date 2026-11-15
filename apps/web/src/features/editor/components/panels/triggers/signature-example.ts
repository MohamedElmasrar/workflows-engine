/** Copy-paste curl example that sends a correctly signed webhook (HMAC-SHA256 over timestamp + body). */

export function signatureExample(url: string, secret: string): string {
  return [
    `SECRET='${secret}'`,
    `BODY='{"orderId":"A-1001","amount":250}'`,
    "TS=$(date +%s)",
    `SIG="sha256=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $NF}')"`,
    `curl -X POST '${url}' \\`,
    "  -H 'Content-Type: application/json' \\",
    '  -H "X-Webhook-Timestamp: $TS" \\',
    '  -H "X-Webhook-Signature: $SIG" \\',
    '  -H "X-Webhook-Delivery-Id: $(uuidgen)" \\',
    '  -d "$BODY"',
  ].join("\n");
}
