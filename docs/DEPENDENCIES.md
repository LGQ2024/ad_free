# 第三方组件与许可证

完整直接/传递组件清单位于：

- `audit-output/sbom.json`（CycloneDX 1.6，机器可读）
- `audit-output/dependencies.csv`（便于人工查看）
- `app/gradle.lockfile`（精确解析版本与配置）

## APK 直接依赖

| 组件 | 固定版本 | 用途 | 许可证 |
|---|---:|---|---|
| AndroidX Core KTX | 1.18.0 | Android 基础 API | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | 单 Activity Compose 宿主 | Apache-2.0 |
| AndroidX Lifecycle Runtime/Compose/ViewModel | 2.10.0 | 生命周期状态收集 | Apache-2.0 |
| Jetpack Compose UI/Material3 | BOM 2026.08.00 | 本地 UI | Apache-2.0 |
| Kotlin 标准库 | 2.3.21 | 语言运行时 | Apache-2.0 |
| kotlinx-coroutines-android | 1.10.2 | 有界异步任务 | Apache-2.0 |

测试依赖 JUnit 4.13.2（EPL-1.0）与 kotlinx-coroutines-test 1.10.2（Apache-2.0），不进入 APK。

构建期 CycloneDX Gradle Plugin 3.3.0 和 OSV-Scanner 2.4.0 只在构建环境运行，不进入 APK。anti-AD 规则快照采用 MIT 许可证，原文在 `app/src/main/assets/anti-ad-LICENSE.txt`。
