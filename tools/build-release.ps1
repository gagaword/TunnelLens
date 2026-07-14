[CmdletBinding()]
param(
    [string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA "TunnelLens\signing")
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
$keystorePath = Join-Path $SigningDirectory "TunnelLens-release.p12"
$credentialPath = Join-Path $SigningDirectory "release-signing.credential.xml"

if (-not (Test-Path -LiteralPath $keystorePath)) {
    throw "Release keystore not found: $keystorePath"
}
if (-not (Test-Path -LiteralPath $credentialPath)) {
    throw "DPAPI-protected signing credential not found: $credentialPath"
}

$credential = Import-Clixml -LiteralPath $credentialPath
$password = $credential.GetNetworkCredential().Password

$env:TUNNELLENS_SIGNING_STORE_FILE = $keystorePath
$env:TUNNELLENS_SIGNING_STORE_PASSWORD = $password
$env:TUNNELLENS_SIGNING_KEY_ALIAS = $credential.UserName
$env:TUNNELLENS_SIGNING_KEY_PASSWORD = $password

Push-Location $projectRoot
try {
    & .\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Release build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
    Remove-Item Env:TUNNELLENS_SIGNING_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:TUNNELLENS_SIGNING_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:TUNNELLENS_SIGNING_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:TUNNELLENS_SIGNING_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
