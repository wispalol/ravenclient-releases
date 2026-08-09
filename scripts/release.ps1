param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [switch]$Installer
)

# Builds a release and publishes the self-update artifacts.
#
# The SOURCE repo (wispalol/ravenclient) is private, so the files installed
# clients need - update.json + the update zip (and optionally the setup .exe) -
# are pushed to a separate PUBLIC repo (wispalol/ravenclient-releases) that the
# launcher can download without any credentials.
#
# Usage:  .\scripts\release.ps1 -Version 1.0.1 [-Installer]
#   -Installer  also runs mvn with -Pinstaller (needs iscc on PATH).
#
# Requires git, Maven and a GitHub credential stored for https://github.com
# (Windows Credential Manager / git credential fill). Adjust $SourceRepo and
# $ReleaseRepo below if the repos change.

$ErrorActionPreference = 'Stop'
$SourceRepo = 'wispalol/ravenclient'
$ReleaseRepo = 'wispalol/ravenclient-releases'
$Tag = "v$Version"
$ZipName = "RavenClient-update-$Version.zip"
$SetupName = "RavenClient_$Version.exe"

# PowerShell 5.1 Set-Content -Encoding UTF8 writes a BOM, which breaks javac/JSON.
function Write-NoBom([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Version must look like '1.2.3', got '$Version'"
}

# --- bump version in pom.xml (first <version> tag = project version) and ClientVersion ---
$pom = Get-Content -Raw pom.xml
$first = [regex]::Match($pom, '<version>[\d.]+</version>')
if (-not $first.Success) { throw 'Could not find the project <version> in pom.xml' }
$pom = $pom.Substring(0, $first.Index) + "<version>$Version</version>" + $pom.Substring($first.Index + $first.Length)
Write-NoBom (Join-Path $PSScriptRoot '..\pom.xml') $pom

$cvPath = 'src\main\java\org\ravenclient\updater\ClientVersion.java'
$cv = Get-Content -Raw $cvPath
$cv = $cv -replace '(VERSION = ")[^"]+(")', "`${1}$Version`${2}"
Write-NoBom (Join-Path $PSScriptRoot "..\$cvPath") $cv

# --- build (clean: incremental builds can ship stale classes in the jar) ---
Write-Host "==> mvn clean package -DskipTests$($(if ($Installer) {' -Pinstaller'} else {''}))"
$buildArgs = @('clean', 'package', '-DskipTests', '-q')
if ($Installer) { $buildArgs += '-Pinstaller' }
mvn @buildArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)" }

# --- checksum + manifest (points at the PUBLIC release repo) ---
$target = Join-Path $PSScriptRoot '..\target'
$zip = Join-Path $target $ZipName
if (-not (Test-Path -LiteralPath $zip)) {
    throw "Expected update zip not found: $zip (run with -Installer and check the build)"
}
$sha = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    version = $Version
    notes   = "RavenClient $Version release."
    url     = "https://github.com/$ReleaseRepo/releases/download/$Tag/$ZipName"
    sha256  = $sha
}
Write-NoBom (Join-Path $PSScriptRoot '..\update.json') (ConvertTo-Json $manifest -Depth 3)
Write-Host "==> update.json updated (sha256=$sha)"

# --- push source + manifest to the private source repo ---
git add pom.xml $cvPath update.json
git commit -m "Release $Version"
git tag $Tag
git push origin HEAD
git push origin $Tag
if ($LASTEXITCODE -ne 0) { throw 'git push to source repo failed' }

# --- publish to the PUBLIC releases repo ---
$cred = @('protocol=https', 'host=github.com', '') -join "`n" | git credential fill 2>$null
$tok = ($cred | Where-Object { $_ -like 'password=*' }) -replace 'password=', ''
if (-not $tok) { throw 'No GitHub credential found for github.com' }
$headers = @{ Authorization = "Bearer $tok"; 'User-Agent' = 'ravenclient-release' }

# 1) update.json -> contents API (replace existing file, so grab its current sha)
$contentB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((Get-Content -Raw update.json)))
try {
    $existing = Invoke-RestMethod -Uri "https://api.github.com/repos/$ReleaseRepo/contents/update.json" `
        -Headers $headers -Method Get
    $fileSha = $existing.sha
} catch {
    $fileSha = $null
}
$putBody = @{ message = "RavenClient $Version"; content = $contentB64 } | ConvertTo-Json
if ($fileSha) { $putBody = (($putBody | ConvertFrom-Json) | Add-Member -NotePropertyName sha -NotePropertyValue $fileSha -PassThru) | ConvertTo-Json }
Invoke-RestMethod -Uri "https://api.github.com/repos/$ReleaseRepo/contents/update.json" `
    -Headers $headers -Method Put -ContentType 'application/json' -Body $putBody | Out-Null
Write-Host "==> update.json published to $ReleaseRepo"

# 2) GitHub release + assets (zip, and the setup .exe if -Installer produced one)
$releaseBody = @{
    tag_name = $Tag
    name     = "RavenClient $Version"
    body     = "RavenClient $Version`n`nUpdate zip: $ZipName"
} | ConvertTo-Json
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$ReleaseRepo/releases" `
    -Headers $headers -Method Post -ContentType 'application/json' -Body $releaseBody
$uploadBase = ($release.upload_url -replace '\{.*', '')
Invoke-RestMethod -Uri "$uploadBase`?name=$ZipName" -Headers $headers -Method Post `
    -ContentType 'application/octet-stream' -InFile $zip | Out-Null
if (Test-Path -LiteralPath (Join-Path $target $SetupName)) {
    Invoke-RestMethod -Uri "$uploadBase`?name=$SetupName" -Headers $headers -Method Post `
        -ContentType 'application/octet-stream' -InFile (Join-Path $target $SetupName) | Out-Null
}

Write-Host "==> Done."
Write-Host "    Release: $($release.html_url)"
Write-Host "    Manifest: https://raw.githubusercontent.com/$ReleaseRepo/main/update.json"
Write-Host "    Note: installed clients will self-update on next launch once the version exceeds theirs."
