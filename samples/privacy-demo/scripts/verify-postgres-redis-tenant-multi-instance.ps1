param(
    [string]$Node1BaseUrl = 'http://localhost:8088',
    [string]$Node2BaseUrl = 'http://localhost:8091',
    [string]$ReceiverPath = '/demo-alert-receiver',
    [string]$BearerToken = 'demo-receiver-token',
    [string]$SignatureSecret = 'demo-receiver-secret',
    [string]$SignatureAlgorithm = 'HmacSHA256',
    [string]$AdminToken = 'demo-admin-token',
    [string]$TenantId = '',
    [string]$ExpectedReplayNamespace = ''
)

if ($SignatureAlgorithm -ne 'HmacSHA256') {
    throw "Only HmacSHA256 is supported by this helper script. Received: $SignatureAlgorithm"
}

$payload = @{
    state = 'WARNING'
    total = 2
    warningThreshold = 1
    downThreshold = 5
    byAction = @{ READ = 2 }
} | ConvertTo-Json -Depth 5

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$nonce = [guid]::NewGuid().ToString()
$mac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($SignatureSecret))
$signatureBytes = $mac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes("$timestamp.$nonce.$payload"))
$signature = ([System.BitConverter]::ToString($signatureBytes)).Replace('-', '').ToLowerInvariant()

$receiverHeaders = @{
    Authorization = "Bearer $BearerToken"
    'X-Privacy-Alert-Timestamp' = $timestamp
    'X-Privacy-Alert-Nonce' = $nonce
    'X-Privacy-Alert-Signature' = $signature
}

$adminHeaders = @{ 'X-Demo-Admin-Token' = $AdminToken }
if ($TenantId) {
    $adminHeaders['X-Privacy-Tenant'] = $TenantId
}

Write-Host "Checking instance metadata..."
Invoke-RestMethod -Method Get -Uri "$Node1BaseUrl/demo-tenants/current" | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Get -Uri "$Node2BaseUrl/demo-tenants/current" | ConvertTo-Json -Depth 5

Write-Host "Posting signed alert to postgres/redis node 1..."
Invoke-RestMethod -Method Post -Uri "$Node1BaseUrl$ReceiverPath" -Headers $receiverHeaders -ContentType 'application/json' -Body $payload | ConvertTo-Json -Depth 5

Write-Host "Replaying the same signed alert against postgres/redis node 2. Expecting replay protection..."
try {
    Invoke-RestMethod -Method Post -Uri "$Node2BaseUrl$ReceiverPath" -Headers $receiverHeaders -ContentType 'application/json' -Body $payload | ConvertTo-Json -Depth 5
    throw "Expected node 2 to reject the replayed nonce, but the request succeeded."
} catch {
    $response = $_.Exception.Response
    if ($null -eq $response) {
        throw
    }
    $statusCode = [int]$response.StatusCode
    if ($statusCode -ne 409) {
        throw "Expected HTTP 409 from node 2, received $statusCode"
    }
    Write-Host "Node 2 rejected the replayed nonce with HTTP 409 as expected."
}

Write-Host "Checking replay-store visibility from postgres/redis node 2..."
$replayStore = Invoke-RestMethod -Method Get -Uri "$Node2BaseUrl/demo-alert-receiver/replay-store?limit=20&offset=0" -Headers $adminHeaders
$replayStore | ConvertTo-Json -Depth 5

if ($ExpectedReplayNamespace) {
    $expectedStorageKey = "$ExpectedReplayNamespace:$nonce"
    $actualStorageKeys = @($replayStore.entries | ForEach-Object { $_.storageKey })
    if ($actualStorageKeys -notcontains $expectedStorageKey) {
        throw "Expected replay-store to contain storage key $expectedStorageKey, actual keys: $($actualStorageKeys -join ', ')"
    }
}

Write-Host "Fetching observability summaries..."
$node1 = Invoke-RestMethod -Method Get -Uri "$Node1BaseUrl/demo-tenants/observability" -Headers $adminHeaders
$node2 = Invoke-RestMethod -Method Get -Uri "$Node2BaseUrl/demo-tenants/observability" -Headers $adminHeaders
$node1 | ConvertTo-Json -Depth 6
$node2 | ConvertTo-Json -Depth 6

if ($node1.receiverReplayStore.backend -ne 'REDIS' -or $node2.receiverReplayStore.backend -ne 'REDIS') {
    throw 'Expected both nodes to expose receiverReplayStore.backend=REDIS'
}

if ($node1.auditRepositoryType -ne 'JDBC' -or $node2.auditRepositoryType -ne 'JDBC') {
    throw 'Expected both nodes to use JDBC audit repositories'
}
