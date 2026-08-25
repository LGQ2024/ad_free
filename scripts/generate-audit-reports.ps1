[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDirectory = Join-Path $projectRoot 'audit-output'
$sbomPath = Join-Path $auditDirectory 'sbom.json'
$scannerSbomPath = Join-Path $auditDirectory 'sbom.cdx.json'
$csvPath = Join-Path $auditDirectory 'dependencies.csv'
$summaryPath = Join-Path $auditDirectory 'dependencies-summary.md'

if (-not (Test-Path -LiteralPath $sbomPath -PathType Leaf)) {
    throw "找不到 CycloneDX SBOM：$sbomPath"
}

$sbom = Get-Content -Raw -LiteralPath $sbomPath -Encoding UTF8 | ConvertFrom-Json
$components = @($sbom.components)

$rows = $components |
    Sort-Object group, name, version |
    ForEach-Object {
        [pscustomobject]@{
            Type = $_.type
            Group = $_.group
            Name = $_.name
            Version = $_.version
            Licenses = (@($_.licenses) | ForEach-Object {
                if ($null -ne $_.license.id) { $_.license.id } else { $_.license.name }
            }) -join ';'
            Purl = $_.purl
        }
    }

$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8
Copy-Item -LiteralPath $sbomPath -Destination $scannerSbomPath -Force

$sbomHash = (Get-FileHash -LiteralPath $sbomPath -Algorithm SHA256).Hash.ToLowerInvariant()
$summary = @(
    '# Release 依赖清单摘要'
    ''
    "- CycloneDX 规范版本：$($sbom.specVersion)"
    "- Release 组件数：$($components.Count)"
    ('- `sbom.json` SHA-256：`{0}`' -f $sbomHash)
    '- 完整机器可读清单：`sbom.json`'
    '- 人工核对清单：`dependencies.csv`'
)
$summary | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Host "已生成依赖报告：$csvPath"
