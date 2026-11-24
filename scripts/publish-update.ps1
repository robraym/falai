[CmdletBinding()]
param(
    [string]$Metadata,
    [string]$Aapt,
    [string]$ApkSigner,
    [switch]$CheckOnly,
    [switch]$Preview
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$repository = 'robraym/falei'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

function Get-PublishHeaders {
    # Capture Git Credential Manager output in memory; never log or persist credentials.
    $credentialOutput = "protocol=https`nhost=github.com`n`n" | & git -c credential.interactive=never credential fill 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Faça login no GitHub pelo Android Studio (Settings > Version Control > GitHub) e tente novamente. O Git Credential Manager precisa estar autenticado para github.com.' }
    $password = $null
    foreach ($line in $credentialOutput) {
        if ($line.StartsWith('password=')) { $password = $line.Substring(9) }
    }
    if ([string]::IsNullOrWhiteSpace($password)) { throw 'O Git não forneceu uma credencial do GitHub. Autentique o Git Credential Manager antes de publicar.' }
    return @{ Authorization = "Bearer $password"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28'; 'User-Agent' = 'Falai-publisher' }
}

function Invoke-GitHub([string]$Method, [string]$Url, $Body = $null) {
    $parameters = @{ Method = $Method; Uri = $Url; Headers = $script:headers; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress))
    }
    try { return Invoke-RestMethod @parameters }
    catch {
        $status = if ($null -ne $_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        throw "O GitHub recusou a operação ($Method, HTTP $status). Confira o login, a conexão e a permissão de escrita no repositório."
    }
}

if (-not $Preview) {
    $script:headers = Get-PublishHeaders
    $repo = Invoke-GitHub 'GET' "https://api.github.com/repos/$repository"
    if (-not $repo.permissions.push) { throw 'A conta autenticada não tem permissão de escrita no repositório Falai.' }
    if ($CheckOnly) {
        Write-Output "Login e permissão de publicação confirmados: $repository."
        exit 0
    }
}

if (-not (Test-Path -LiteralPath $Metadata -PathType Leaf)) { throw 'Os metadados do APK não foram encontrados. Gere o APK antes de publicar.' }
$metadataPath = (Resolve-Path -LiteralPath $Metadata).Path
$data = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if ($data.applicationId -ne 'br.com.falai' -or @($data.elements).Count -ne 1) { throw 'Esperado um único APK do aplicativo br.com.falai.' }
if ($data.variantName -ne 'release') { throw 'A publicação aceita somente APK release.' }
$element = $data.elements[0]
$apk = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $metadataPath) $element.outputFile))
if ([IO.Path]::GetDirectoryName($apk) -ne [IO.Path]::GetDirectoryName($metadataPath) -or -not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'Caminho de APK inválido nos metadados.' }
if (-not (Test-Path -LiteralPath $Aapt -PathType Leaf)) { throw 'A ferramenta aapt do Android SDK não foi encontrada.' }
$badging = & $Aapt dump badging $apk 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível ler a versão dentro do APK.' }
if ($badging -match '^application-debuggable') { throw 'O APK ainda permite depuração. Gere a variante release.' }
if (-not (Test-Path -LiteralPath $ApkSigner -PathType Leaf)) { throw 'A ferramenta apksigner não foi encontrada.' }
$signature = & $ApkSigner verify --verbose --print-certs $apk 2>$null
if ($LASTEXITCODE -ne 0) { throw 'A assinatura do APK não é válida.' }
$expectedCertificate = '1abbd98acf903faff2b50faf7cd021b61a1cea2d445669557c2b6e089506ab8a'
if (-not ($signature -match "^Signer #1 certificate SHA-256 digest: $expectedCertificate$")) {
    throw 'O certificado difere do Falai já distribuído. Use a chave original para preservar a atualização dos apps instalados.'
}
$packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
if ($packageLine -notmatch "name='br.com.falai' versionCode='([0-9]+)' versionName='([0-9]+\.[0-9]+)'") { throw 'O APK deve ser do Falai e ter versão no formato 1.2.' }
$code = $Matches[1]
$version = $Matches[2]
if ([string]$element.versionCode -ne $code -or [string]$element.versionName -ne $version) { throw 'A versão dos metadados difere da versão do APK. Gere novamente.' }
$tag = "v$version-apk.$code"
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item -LiteralPath $apk).Length
if ($size -le 0 -or $size -gt 209715200) { throw 'O tamanho do APK está fora do limite aceito pelo atualizador.' }
Write-Output "APK: $apk"
Write-Output "Versão: $version | versionCode: $code | Tag: $tag"
Write-Output 'Asset: falai.apk (compatível com as versões já instaladas).'
if ($Preview) { Write-Output 'Prévia concluída. Nenhuma publicação realizada.'; exit 0 }

$commit = & git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível identificar o commit do projeto.' }
$null = Invoke-GitHub 'GET' "https://api.github.com/repos/$repository/commits/$commit"
$release = Invoke-GitHub 'POST' "https://api.github.com/repos/$repository/releases" @{
    tag_name = $tag; target_commitish = "$commit"; name = "Falai $version"; draft = $true; prerelease = $false
    body = "Atualização do Falai $version.`n`nCódigo do APK: $code`nSHA-256: $hash"
}
try {
    $uploadUrl = ($release.upload_url -replace '\{.*$', '') + '?name=falai.apk'
    $asset = Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $script:headers -ContentType 'application/vnd.android.package-archive' -InFile $apk
    if ($asset.state -ne 'uploaded' -or $asset.name -ne 'falai.apk' -or $asset.size -ne $size) { throw 'O upload não foi confirmado pelo GitHub.' }
    if ($asset.digest -and $asset.digest -ne "sha256:$hash") { throw 'O hash publicado difere do APK local.' }
    $published = Invoke-GitHub 'PATCH' "https://api.github.com/repos/$repository/releases/$($release.id)" @{ draft = $false; make_latest = 'true' }
    Write-Output "Atualização publicada: $($published.html_url)"
} catch {
    # A failed upload leaves only a draft; never replace a previously released APK.
    throw "A publicação não foi concluída. Confira o rascunho em https://github.com/$repository/releases antes de tentar novamente. Nenhum arquivo existente foi substituído."
}
