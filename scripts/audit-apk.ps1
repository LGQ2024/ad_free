[CmdletBinding()]
param(
    [string]$ApkPath = '.\app\build\outputs\apk\release\app-release.apk',
    [string]$JavaHome = 'E:\Program Files\Android\Android Studio\jbr'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDirectory = Join-Path $projectRoot 'audit-output'
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$localProperties = Join-Path $projectRoot 'local.properties'
$java = Join-Path $JavaHome 'bin\java.exe'

function Get-AndroidSdkPath {
    $line = Get-Content -LiteralPath $localProperties -Encoding UTF8 |
        Where-Object { $_ -like 'sdk.dir=*' } |
        Select-Object -First 1
    if ($null -eq $line) {
        throw 'local.properties 中缺少 sdk.dir。'
    }
    $value = $line.Substring('sdk.dir='.Length)
    $value.Replace('\:', ':').Replace('\\', '\')
}

function Invoke-ApkAnalyzer {
    param([string[]]$Arguments)

    $result = @(
        & $java `
            "-Dcom.android.sdklib.toolsdir=$commandLineTools" `
            -classpath $apkAnalyzerClasspath `
            com.android.tools.apk.analyzer.ApkAnalyzerCli `
            @Arguments 2>&1
    )
    if ($LASTEXITCODE -ne 0) {
        throw "apkanalyzer 执行失败：$($Arguments -join ' ')"
    }
    ($result | ForEach-Object { [string]$_ }) -join [Environment]::NewLine
}

function Write-Report {
    param([string]$Name, [string]$Content)
    $path = Join-Path $auditDirectory $Name
    $Content | Set-Content -LiteralPath $path -Encoding UTF8
    $path
}

New-Item -ItemType Directory -Path $auditDirectory -Force | Out-Null

$sdk = Get-AndroidSdkPath
$commandLineTools = Join-Path $sdk 'cmdline-tools\latest'
$apkAnalyzerClasspath = Join-Path $commandLineTools 'lib\apkanalyzer-classpath.jar'
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') |
    Where-Object { $_.PSIsContainer -and $_.Name -match '^\d+(\.\d+)+$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
$apkSigner = Join-Path $buildTools.FullName 'apksigner.bat'

foreach ($requiredTool in @($java, $apkAnalyzerClasspath, $apkSigner)) {
    if (-not (Test-Path -LiteralPath $requiredTool -PathType Leaf)) {
        throw "找不到审计工具：$requiredTool"
    }
}

$oldJavaHome = $env:JAVA_HOME
$env:JAVA_HOME = $JavaHome
try {
    $signingReport = @(& $apkSigner verify --verbose --print-certs $resolvedApk 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw 'APK 签名验证失败。'
    }
    Write-Report 'signing-report.txt' (($signingReport | ForEach-Object { [string]$_ }) -join [Environment]::NewLine) | Out-Null

    $apkHash = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Report 'apk-sha256.txt' "$apkHash  app-release.apk" | Out-Null

    $permissionsText = Invoke-ApkAnalyzer @('manifest', 'permissions', $resolvedApk)
    Write-Report 'permissions-report.txt' $permissionsText | Out-Null
    $actualPermissions = @(
        $permissionsText -split '\r?\n' |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -like 'android.permission.*' } |
            Sort-Object -Unique
    )
    $expectedPermissions = @(
        'android.permission.ACCESS_NETWORK_STATE'
        'android.permission.FOREGROUND_SERVICE'
        'android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED'
        'android.permission.INTERNET'
        'android.permission.POST_NOTIFICATIONS'
        'android.permission.QUERY_ALL_PACKAGES'
    ) | Sort-Object
    $permissionDifference = @(Compare-Object $expectedPermissions $actualPermissions)
    if ($permissionDifference.Count -ne 0) {
        throw 'APK 权限与允许列表不一致，请检查 permissions-report.txt。'
    }

    $manifestText = Invoke-ApkAnalyzer @('manifest', 'print', $resolvedApk)
    Write-Report 'manifest-report.xml' $manifestText | Out-Null
    [xml]$manifest = $manifestText
    $namespaceManager = [Xml.XmlNamespaceManager]::new($manifest.NameTable)
    $namespaceManager.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
    $exportedNodes = @($manifest.SelectNodes('//*[@android:exported="true"]', $namespaceManager))
    if ($exportedNodes.Count -ne 1) {
        throw "预期只有一个导出组件，实际为 $($exportedNodes.Count) 个。"
    }
    $exportedName = $exportedNodes[0].GetAttribute('name', 'http://schemas.android.com/apk/res/android')
    if ($exportedName -ne 'com.example.jingwang.MainActivity') {
        throw "发现意外导出组件：$exportedName"
    }

    $debuggable = (Invoke-ApkAnalyzer @('manifest', 'debuggable', $resolvedApk)).Trim()
    if ($debuggable -ne 'false') {
        throw 'Release APK 被标记为 debuggable。'
    }

    $filesText = Invoke-ApkAnalyzer @('files', 'list', '--files-only', $resolvedApk)
    Write-Report 'apk-files-report.txt' $filesText | Out-Null
    if ($filesText -match '(?im)(^|/)lib/[^\r\n]+\.so$') {
        throw 'APK 中发现原生 .so 库。'
    }

    $definedPackages = Invoke-ApkAnalyzer @('dex', 'packages', '--defined-only', $resolvedApk)
    Write-Report 'dex-packages-report.txt' $definedPackages | Out-Null
    $forbiddenDefinedPackages = @(
        'com.google.android.gms.ads'
        'com.google.firebase.analytics'
        'com.google.firebase.crashlytics'
        'com.facebook.ads'
        'com.appsflyer'
        'okhttp3'
        'retrofit2'
    )
    foreach ($pattern in $forbiddenDefinedPackages) {
        if ($definedPackages -match [regex]::Escape($pattern)) {
            throw "APK 中发现禁止的 SDK/包：$pattern"
        }
    }

    $allPackages = Invoke-ApkAnalyzer @('dex', 'packages', $resolvedApk)
    $forbiddenApiReferences = @(
        'android.webkit.WebView'
        'dalvik.system.DexClassLoader'
        'dalvik.system.PathClassLoader'
    )
    foreach ($pattern in $forbiddenApiReferences) {
        if ($allPackages -match [regex]::Escape($pattern)) {
            throw "APK 中发现禁止的 API 引用：$pattern"
        }
    }

    $networkCode = Invoke-ApkAnalyzer @(
        'dex',
        'code',
        '--class',
        'com.example.jingwang.data.RuleRepository',
        $resolvedApk
    )
    $hosts = @(
        [regex]::Matches($networkCode, 'https://([A-Za-z0-9.-]+)') |
            ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } |
            Sort-Object -Unique
    )
    $networkReport = @(
        '规则更新代码中检测到的固定 HTTPS 主机：'
        ($hosts | ForEach-Object { "- $_" })
    ) -join [Environment]::NewLine
    Write-Report 'network-boundary-report.txt' $networkReport | Out-Null
    if ($hosts.Count -ne 1 -or $hosts[0] -ne 'anti-ad.net') {
        throw '规则更新代码中的固定 HTTPS 主机不符合允许列表。'
    }

    Write-Host "APK 自动审计通过：$resolvedApk"
    Write-Host "APK SHA-256：$apkHash"
} finally {
    if ($null -eq $oldJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $oldJavaHome
    }
}
