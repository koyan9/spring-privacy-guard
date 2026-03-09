param(
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$BearerToken = 'demo-receiver-token',
    [string]$SignatureSecret = 'demo-receiver-secret',
    [string]$SignatureAlgorithm = 'HmacSHA256',
    [string]$AdminToken = 'demo-admin-token'
)

$payload = @{
    state = 'WARNING'
    total = 2
    warningThreshold = 1
    downThreshold = 5
    byAction = @{ READ = 2 }
} | ConvertTo-Json -Depth 5

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$nonce = [guid]::NewGuid().ToString()
if ($SignatureAlgorithm -ne 'HmacSHA256') {
    throw "Only HmacSHA256 is supported by this helper script. Received: $SignatureAlgorithm"
}
$mac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($SignatureSecret))
$signatureBytes = $mac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes("$timestamp.$nonce.$payload"))
$signature = ([System.BitConverter]::ToString($signatureBytes)).Replace('-', '').ToLowerInvariant()

$receiverHeaders = @{
    Authorization = "Bearer $BearerToken"
    'X-Privacy-Alert-Timestamp' = $timestamp
    'X-Privacy-Alert-Nonce' = $nonce
    'X-Privacy-Alert-Signature' = $signature
}

Write-Host "Posting signed alert to $BaseUrl/demo-alert-receiver ..."
Invoke-RestMethod -Method Post -Uri "$BaseUrl/demo-alert-receiver" -Headers $receiverHeaders -ContentType 'application/json' -Body $payload | ConvertTo-Json -Depth 5

Write-Host "Fetching stored receiver payload from $BaseUrl/demo-alert-receiver/last ..."
Invoke-RestMethod -Method Get -Uri "$BaseUrl/demo-alert-receiver/last" -Headers @{ 'X-Demo-Admin-Token' = $AdminToken } | ConvertTo-Json -Depth 5
