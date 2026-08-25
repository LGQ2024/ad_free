# 净网

“净网”是一个 Android 8.0+ 的本地 DNS 广告域名拦截器。它通过 `VpnService` 只路由两个虚拟 DNS 地址，不接管普通 TCP/UDP 流量。广告域名返回 `NXDOMAIN`，其他查询只转发到 Android 当前底层网络提供的系统 DNS。

## 隐私设计

- 不读取网页正文；应用源码不使用 WebView、JavaScript、NDK/JNI、反射或动态代码加载。
- 不含账号、广告、统计、联网崩溃上报、远程配置、第三方 HTTP 或日志 SDK。
- DNS 查询日志最多 500 条且仅在内存；停止 VPN、进程退出或重启后清空。
- Java/Kotlin 未捕获异常只保留最近一次本地报告；用户可在设置页查看、复制或删除，应用不会自动上传。
- 白名单、绕过包名、规则元数据和数字统计使用 Android Keystore 的不可导出 AES-256-GCM 密钥加密，并由 `AtomicFile` 原子写入。
- 只在用户点击更新时访问固定的 `https://anti-ad.net/domains.txt`，不自动更新、不跟随重定向。
- 禁止明文 HTTP、云备份和设备迁移备份。

完整边界见 [PRIVACY.md](PRIVACY.md)、[SECURITY.md](SECURITY.md) 和 [THREAT_MODEL.md](THREAT_MODEL.md)。

## 构建状态

- `compileSdk/targetSdk 37`、`minSdk 26`
- AGP 9.3.1、Kotlin 2.3.21、Gradle 9.7.0、Compose BOM 2026.08.00
- `testReleaseUnitTest`、`lintRelease`、Release Kotlin/Java 编译：已通过
- CycloneDX 1.6 SBOM：已生成
- OSV-Scanner 2.4.0 离线扫描：103 个组件，0 个已知漏洞
- 真机 VPN、网络切换和系统行为：尚未验证（当前没有连接设备或模拟器）
- Debug APK：已生成，仅供用户本人安装测试
- 正式签名 APK：尚未生成

Release 构建在签名变量缺失时仍会立即停止，不会回退到调试签名。根据用户后续选择，当前单独提供一个明确标记为 Debug 的测试包。

当前 Compose BOM 2026.08.00 会通过 AndroidX `graphics-path` 带入四个小型原生图形库；它们不是本项目编写的 JNI 代码。若要求最终 APK 完全不含原生库，需要把 Compose UI 改写为传统 Android View。

APK、AAB、签名密钥和本机配置不会提交到仓库；维护者使用 Android Studio 的 `Build → Generate Signed App Bundle or APK` 在本机完成正式签名。

## 许可证

项目源码采用 [MIT License](LICENSE)。内置 anti-AD 规则快照采用其随附的 MIT 许可证。

## 自动验证

```powershell
$env:JAVA_HOME='E:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:cyclonedxDirectBom --offline --console=plain
```

正式构建请按 [docs/SIGNING.md](docs/SIGNING.md) 操作。Android Studio 不是下载依赖或完成自动检查的必要条件，可在后续连接真机时使用。
