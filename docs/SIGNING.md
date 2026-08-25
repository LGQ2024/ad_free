# 正式签名与交付

## 1. 项目外密钥

正式签名密钥库必须保存在项目目录外，绝不能复制进仓库。

密钥库及密码由用户自行保管。密码不要通过聊天传递，不写入源码、命令行或日志。丢失密钥后无法用同一身份升级已安装应用。

## 2. 构建

### Android Studio

使用 `Build → Generate Signed App Bundle or APK`，选择 APK、Release 和项目外的 PKCS12 密钥库。Android Studio 会临时注入签名信息；项目接受这种方式，但不会把密码写入源码或 Gradle 文件。

不要直接从 Gradle 工具窗口运行裸 `assembleRelease`，除非已经设置下述环境变量；裸任务没有 Android Studio 向导提供的临时签名信息。

### PowerShell

运行仓库脚本；密码用安全输入读取，不回显、不写入文件，脚本结束后清除进程环境变量。

```powershell
.\scripts\build-release.ps1 `
  -KeyStorePath 'D:\private-keys\jingwang-release.p12'
```

脚本会隐藏读取密码，并在密钥库只有一个别名时自动识别；密码只通过当前构建进程的环境变量传递，脚本结束后立即清除。脚本随后运行 Release 单元测试、lint、SBOM、OSV 离线扫描、`assembleRelease`、`apksigner verify` 和 APK 静态检查。没有完整签名信息时 Gradle 会停止，不生成可交付 Release APK。

## 3. 双构建复现

分别执行两次全新构建，把两个 APK 保存到不同位置，然后运行：

```powershell
.\scripts\verify-reproducible.ps1 `
  -FirstApk 'D:\build-1\app-release.apk' `
  -SecondApk 'D:\build-2\app-release.apk'
```

SHA-256 必须一致。不一致时不得交付，应先比较 ZIP 条目、资源和签名块。

## 4. 交付文件

- `app-release.apk`
- `audit-output/apk-sha256.txt`
- `audit-output/signing-report.txt`
- `audit-output/permissions-report.txt`
- `audit-output/apk-files-report.txt`
- `audit-output/dex-packages-report.txt`
- `audit-output/sbom.json`
- `audit-output/osv-report.json`
