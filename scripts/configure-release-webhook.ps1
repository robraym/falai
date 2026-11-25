[CmdletBinding()]
param([switch]$Configure, [switch]$Verify)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$repository = 'robraym/falai'
$endpoint = 'https://falai-notificacoes.robraym-studio.workers.dev/hooks/github/releases'
# Credentials and the webhook secret remain only in process memory; never print or save them.
$credential = "protocol=https`nhost=github.com`n`n" | & git -c credential.interactive=never credential fill 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Autentique o Git Credential Manager para configurar o webhook.' }
$token = $null
foreach ($line in $credential) { if ($line.StartsWith('password=')) { $token = $line.Substring(9) } }
if (-not $token) { throw 'Credencial do GitHub indisponível.' }
$headers = @{Authorization = "Bearer $token"; Accept = 'application/vnd.github+json'; 'User-Agent' = 'Falai-webhook-setup'; 'X-GitHub-Api-Version' = '2022-11-28'}
function Invoke-WebhookApi([string]$Method, [string]$Path, $Body = $null) {
    $parameters = @{Method = $Method; Uri = "https://api.github.com/repos/$repository/$Path"; Headers = $headers}
    if ($null -ne $Body) { $parameters.ContentType = 'application/json'; $parameters.Body = $Body | ConvertTo-Json -Depth 6 -Compress }
    try {
        $result = Invoke-RestMethod @parameters
        if ($result -is [Array]) { foreach ($item in $result) { Write-Output $item } }
        else { Write-Output $result }
    }
    catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        throw "Falha ao configurar webhook do GitHub (HTTP $status). É necessária permissão de administração dos webhooks do repositório."
    }
}
$hooks = @(Invoke-WebhookApi 'GET' 'hooks?per_page=100')
$existing = @($hooks | Where-Object { $_.config.url -eq $endpoint })
if ($existing.Count -gt 1) { throw 'Há mais de um webhook para este endpoint. Revise os webhooks do repositório antes de configurar.' }
if (-not $Configure) {
    Write-Output "Acesso aos webhooks confirmado. Webhooks Falai encontrados: $($existing.Count)."
    if ($Verify -and $existing.Count -eq 1) {
        $deliveries = @(Invoke-WebhookApi 'GET' "hooks/$($existing[0].id)/deliveries?per_page=3")
        foreach ($delivery in $deliveries) { Write-Output "Entrega: $($delivery.event) | HTTP $($delivery.status_code) | $($delivery.status)" }
    }
    exit 0
}
$bytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$webhookSecret = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
$worker = Join-Path (Split-Path -Parent $PSScriptRoot) 'worker'
Push-Location -LiteralPath $worker
try {
    $webhookSecret | & '.\node_modules\.bin\wrangler.cmd' secret put GITHUB_RELEASE_WEBHOOK_SECRET
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível salvar o segredo no Worker.' }
} finally { Pop-Location }
$body = @{name = 'web'; active = $true; events = @('release'); config = @{url = $endpoint; content_type = 'json'; insecure_ssl = '0'; secret = $webhookSecret}}
$hook = if ($existing.Count -eq 1) { Invoke-WebhookApi 'PATCH' "hooks/$($existing[0].id)" $body } else { Invoke-WebhookApi 'POST' 'hooks' $body }
$null = Invoke-WebhookApi 'POST' "hooks/$($hook.id)/pings"
Write-Output "Webhook de publicação configurado e ping solicitado. ID: $($hook.id)."
