[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$FirstApk,
    [Parameter(Mandatory)]
    [string]$SecondApk
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$first = (Resolve-Path -LiteralPath $FirstApk).Path
$second = (Resolve-Path -LiteralPath $SecondApk).Path
$firstHash = (Get-FileHash -LiteralPath $first -Algorithm SHA256).Hash.ToLowerInvariant()
$secondHash = (Get-FileHash -LiteralPath $second -Algorithm SHA256).Hash.ToLowerInvariant()

Write-Host "第一次构建：$firstHash"
Write-Host "第二次构建：$secondHash"

if ($firstHash -ne $secondHash) {
    throw '两次 APK 的 SHA-256 不一致，不得交付。'
}

Write-Host '两次 APK 完全一致。'
