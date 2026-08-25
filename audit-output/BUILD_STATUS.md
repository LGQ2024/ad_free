# 自动安全检查状态

检查日期：2026-08-24（Asia/Shanghai）

- Release Kotlin/Java 编译：通过
- `testReleaseUnitTest`：通过
- `lintRelease`：通过
- 依赖锁：已生成
- SHA-256 依赖验证：离线严格复验通过
- PGP：官方签名/密钥获取静默超时，当前未启用；不得声称已完成
- CycloneDX 1.6：103 个 Release 组件
- OSV-Scanner 2.4.0：使用 Maven 离线数据库，0 个已知漏洞
- anti-AD 快照：版本 `20260823025523`，108,157 条，SHA-256 `6e72e1f63319c77e74618b103ab45c37624d83bff3572d03f4712ade262cd6f5`
- Debug APK：构建成功；13/13 单元测试通过，lint 无问题，v2 调试签名验证通过
- Debug APK SHA-256：`6aabb46b90d745c5475b0300262093a8d0058421fc3df4cc2749ffdaaec40715`
- 正式签名 APK：尚未生成
- APK 权限/组件/Dex/签名静态审计：待正式 APK
- 已知 Debug 差异：AndroidX 增加包名绑定的内部签名权限；Compose `graphics-path` 包含四个 ABI 的原生图形库
- 真机 VPN 行为：待设备
