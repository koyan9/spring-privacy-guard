param(
    [string]$Node1BaseUrl = 'http://localhost:8088',
    [string]$Node2BaseUrl = 'http://localhost:8092',
    [string]$BearerToken = 'demo-receiver-token',
    [string]$SignatureSecret = 'demo-receiver-secret',
    [string]$SignatureAlgorithm = 'HmacSHA256',
    [string]$AdminToken = 'demo-admin-token'
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

Write-Host "Checking instance metadata..."
$node1Current = Invoke-RestMethod -Method Get -Uri "$Node1BaseUrl/demo-tenants/current"
$node2Current = Invoke-RestMethod -Method Get -Uri "$Node2BaseUrl/demo-tenants/current"
$node1Current | ConvertTo-Json -Depth 5
$node2Current | ConvertTo-Json -Depth 5

Write-Host "Posting signed alert to custom JDBC node 1..."
Invoke-RestMethod -Method Post -Uri "$Node1BaseUrl/demo-alert-receiver" -Headers $receiverHeaders -ContentType 'application/json' -Body $payload | ConvertTo-Json -Depth 5

Write-Host "Checking replay-store visibility from custom JDBC node 2..."
Invoke-RestMethod -Method Get -Uri "$Node2BaseUrl/demo-alert-receiver/replay-store?limit=20&offset=0" -Headers $adminHeaders | ConvertTo-Json -Depth 5

Write-Host "Replaying the same signed alert against custom JDBC node 2. Expecting replay protection..."
try {
    Invoke-RestMethod -Method Post -Uri "$Node2BaseUrl/demo-alert-receiver" -Headers $receiverHeaders -ContentType 'application/json' -Body $payload | ConvertTo-Json -Depth 5
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

Write-Host "Fetching observability summaries..."
$node1 = Invoke-RestMethod -Method Get -Uri "$Node1BaseUrl/demo-tenants/observability" -Headers $adminHeaders
$node2 = Invoke-RestMethod -Method Get -Uri "$Node2BaseUrl/demo-tenants/observability" -Headers $adminHeaders
$node1 | ConvertTo-Json -Depth 6
$node2 | ConvertTo-Json -Depth 6

if ($node1.repositoryImplementations.audit -ne 'CustomJdbcTenantAuditRepository' -or $node2.repositoryImplementations.audit -ne 'CustomJdbcTenantAuditRepository') {
    throw 'Expected both nodes to use CustomJdbcTenantAuditRepository'
}

if ($node1.repositoryImplementations.deadLetter -ne 'CustomJdbcTenantDeadLetterRepository' -or $node2.repositoryImplementations.deadLetter -ne 'CustomJdbcTenantDeadLetterRepository') {
    throw 'Expected both nodes to use CustomJdbcTenantDeadLetterRepository'
}

if ($node1.receiverReplayStore.backend -ne 'JDBC' -or $node2.receiverReplayStore.backend -ne 'JDBC') {
    throw 'Expected both nodes to expose receiverReplayStore.backend=JDBC'
}
