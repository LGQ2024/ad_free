[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$KeyStorePath,
    [string]$KeyAlias,
    [string]$JavaHome = 'E:\Program Files\Android\Android Studio\jbr',
    [string]$OsvScannerPath = (Join-Path $env:TEMP 'jingwang-osv-2.4.0\osv-scanner_windows_amd64.exe')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedKeyStore = (Resolve-Path -LiteralPath $KeyStorePath).Path
$keytool = Join-Path $JavaHome 'bin\keytool.exe'

if (-not (Test-Path -LiteralPath $resolvedKeyStore -PathType Leaf)) {
    throw "找不到密钥库文件：$KeyStorePath"
}
if (-not (Test-Path -LiteralPath $keytool -PathType Leaf)) {
    throw "找不到 keytool：$keytool"
}

function ConvertTo-PlainText {
    param([Security.SecureString]$SecureValue)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$oldJavaHome = $env:JAVA_HOME
$oldGradleUserHome = $env:GRADLE_USER_HOME
$storePassword = $null
$keyPassword = $null

try {
    $storePassword = ConvertTo-PlainText (Read-Host '请输入密钥库密码' -AsSecureString)
    if ([string]::IsNullOrEmpty($storePassword)) {
        throw '密钥库密码不能为空。'
    }

    $env:JAVA_HOME = $JavaHome
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
    $env:JINGWANG_KEYSTORE_PATH = $resolvedKeyStore
    $env:JINGWANG_KEYSTORE_PASSWORD = $storePassword

    $keytoolOutput = @(
        & $keytool `
            -J-Duser.language=en `
            -J-Duser.country=US `
            -list `
            -v `
            -storetype PKCS12 `
            -keystore $resolvedKeyStore `
            -storepass:env JINGWANG_KEYSTORE_PASSWORD 2>&1
    )
    if ($LASTEXITCODE -ne 0) {
        throw '无法打开 PKCS12 密钥库。请确认文件和密码正确。'
    }

    $aliases = @(
        $keytoolOutput |
            ForEach-Object { [string]$_ } |
            ForEach-Object {
                if ($_ -match '^Alias name:\s*(.+?)\s*$') { $Matches[1] }
            } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Sort-Object -Unique
    )

    if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
        if ($aliases.Count -ne 1) {
            throw "密钥库中检测到 $($aliases.Count) 个别名。请使用 -KeyAlias 明确指定。"
        }
        $KeyAlias = $aliases[0]
    } elseif ($KeyAlias -notin $aliases) {
        throw "密钥库中不存在别名 '$KeyAlias'。"
    }

    $enteredKeyPassword = ConvertTo-PlainText (
        Read-Host '请输入密钥条目密码；如果与密钥库密码相同，直接回车' -AsSecureString
    )
    $keyPassword = if ([string]::IsNullOrEmpty($enteredKeyPassword)) {
        $storePassword
    } else {
        $enteredKeyPassword
    }
    $enteredKeyPassword = $null

    $env:JINGWANG_KEY_ALIAS = $KeyAlias
    $env:JINGWANG_KEY_PASSWORD = $keyPassword

    Write-Host "已识别签名别名：$KeyAlias"
    Write-Host '开始执行 Release 测试、lint、SBOM 和正式构建……'

    Push-Location $projectRoot
    try {
        & .\gradlew.bat `
            :app:testReleaseUnitTest `
            :app:lintRelease `
            :app:cyclonedxDirectBom `
            :app:assembleRelease `
            --offline `
            --no-daemon `
            --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle Release 构建失败，退出码：$LASTEXITCODE"
        }

        & .\scripts\generate-audit-reports.ps1

        if (-not (Test-Path -LiteralPath $OsvScannerPath -PathType Leaf)) {
            throw "找不到固定版本 OSV-Scanner：$OsvScannerPath"
        }
        & $OsvScannerPath scan `
            --sbom .\audit-output\sbom.cdx.json `
            --offline `
            --offline-vulnerabilities `
            --no-resolve `
            --format json `
            --output-file .\audit-output\osv-report.json
        if ($LASTEXITCODE -ne 0) {
            throw "OSV 离线漏洞扫描未通过，退出码：$LASTEXITCODE"
        }

        & .\scripts\audit-apk.ps1 -ApkPath .\app\build\outputs\apk\release\app-release.apk
    } finally {
        Pop-Location
    }

    Write-Host 'Release APK 已构建并通过自动审计。'
} finally {
    Remove-Item Env:JINGWANG_KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:JINGWANG_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:JINGWANG_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:JINGWANG_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($null -eq $oldJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $oldJavaHome
    }
    if ($null -eq $oldGradleUserHome) {
        Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue
    } else {
        $env:GRADLE_USER_HOME = $oldGradleUserHome
    }
    $storePassword = $null
    $keyPassword = $null
}
