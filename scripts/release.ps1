param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [switch]$Installer
)

# Builds a release, uploads the update zip to a GitHub release, and updates the
# self-update manifest the launcher checks (update.json in the repo root).
#
# Usage:  .\scripts\release.ps1 -Version 1.0.1 [-Installer]
#   -Installer  also runs mvn with -Pinstaller (needs iscc on PATH).
#
# Requires git, Maven and a GitHub credential stored for https://github.com
# (Windows Credential Manager / git credential fill). The repo is pushed to
# wispalol/ravenclient; adjust $OwnerRepo below if that changes.

$ErrorActionPreference = 'Stop'
$OwnerRepo = 'wispalol/ravenclient'
$Tag = "v$Version"
$ZipName = "RavenClient-update-$Version.zip"

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Version must look like '1.2.3', got '$Version'"
}

# --- bump version in pom.xml (first <version> tag = project version) and ClientVersion ---
$pom = Get-Content -Raw pom.xml
$pom = [regex]::Replace($pom, '<version>[\d.]+</version>', "<version>$Version</version>", 1)
Set-Content -LiteralPath pom.xml -Value $pom -Encoding UTF8

$cvPath = 'src\main\java\org\ravenclient\updater\ClientVersion.java'
$cv = Get-Content -Raw $cvPath
$cv = $cv -replace '(VERSION = ")[^"]+(")', "`${1}$Version`${2}"
Set-Content -LiteralPath $cvPath -Value $cv -Encoding UTF8

# --- build ---
Write-Host "==> mvn package -DskipTests$($(if ($Installer) {' -Pinstaller'} else {''}))"
$buildArgs = @('package', '-DskipTests', '-q')
if ($Installer) { $buildArgs += '-Pinstaller' }
mvn @buildArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)" }

# --- checksum + manifest ---
$zip = Join-Path (Join-Path $PSScriptRoot '..\target') $ZipName
if (-not (Test-Path -LiteralPath $zip)) {
    throw "Expected update zip not found: $zip (run with -Installer and check the build)"
}
$sha = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    version = $Version
    notes   = "RavenClient $Version release."
    url     = "https://github.com/$OwnerRepo/releases/download/$Tag/$ZipName"
    sha256  = $sha
}
(ConvertTo-Json $manifest -Depth 3) | Set-Content -LiteralPath update.json -Encoding UTF8
Write-Host "==> update.json updated (sha256=$sha)"

# --- push source + manifest ---
git add pom.xml $cvPath update.json
git commit -m "Release $Version"
git tag $Tag
git push origin HEAD
git push origin $Tag
if ($LASTEXITCODE -ne 0) { throw 'git push failed' }

# --- create GitHub release and upload the zip ---
$cred = @('protocol=https', 'host=github.com', '') -join "`n" | git credential fill 2>$null
$tok = ($cred | Where-Object { $_ -like 'password=*' }) -replace 'password=', ''
if (-not $tok) { throw 'No GitHub credential found for github.com' }

$headers = @{ Authorization = "Bearer $tok"; 'User-Agent' = 'ravenclient-release' }
$releaseBody = @{
    tag_name = $Tag
    name     = "RavenClient $Version"
    body     = "RavenClient $Version`n`nUpdate zip: $ZipName"
} | ConvertTo-Json

$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$OwnerRepo/releases" `
    -Headers $headers -Method Post -ContentType 'application/json' -Body $releaseBody

$uploadBase = ($release.upload_url -replace '\{.*', '')
$uploadUrl = "$uploadBase`?name=$ZipName"
Invoke-RestMethod -Uri $uploadUrl -Headers $headers -Method Post `
    -ContentType 'application/octet-stream' -InFile $zip | Out-Null

Write-Host "==> Done."
Write-Host "    Release: $($release.html_url)"
Write-Host "    Manifest: https://raw.githubusercontent.com/$OwnerRepo/main/update.json"
Write-Host "    Note: installed clients will self-update on next launch once the version above the current one."
