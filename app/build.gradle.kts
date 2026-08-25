import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.cyclonedx.bom")
}

val releaseStorePath = providers.environmentVariable("JINGWANG_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("JINGWANG_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("JINGWANG_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("JINGWANG_KEY_PASSWORD").orNull
val hasEnvironmentReleaseSigning = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val injectedStorePath = providers.gradleProperty("android.injected.signing.store.file").orNull
val hasAndroidStudioSigning = listOf(
    injectedStorePath,
    providers.gradleProperty("android.injected.signing.store.password").orNull,
    providers.gradleProperty("android.injected.signing.key.alias").orNull,
    providers.gradleProperty("android.injected.signing.key.password").orNull,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.jingwang"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.jingwang"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    if (hasEnvironmentReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStorePath))
                storeType = "PKCS12"
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    lint {
        // 计划有意固定这些精确版本；可用新版本由独立供应链审计报告追踪。
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencyLocking {
    lockAllConfigurations()
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fails release packaging unless user-owned signing credentials are supplied."
    doLast {
        check(hasEnvironmentReleaseSigning || hasAndroidStudioSigning) {
            "Release signing is required. Use Android Studio's Generate Signed App Bundle or APK wizard, " +
                "or set JINGWANG_KEYSTORE_PATH, JINGWANG_KEYSTORE_PASSWORD, " +
                "JINGWANG_KEY_ALIAS, and JINGWANG_KEY_PASSWORD."
        }
        if (hasEnvironmentReleaseSigning) {
            check(file(requireNotNull(releaseStorePath)).isFile) {
                "JINGWANG_KEYSTORE_PATH does not point to a readable file."
            }
        }
        if (hasAndroidStudioSigning) {
            check(file(requireNotNull(injectedStorePath)).isFile) {
                "Android Studio's injected signing store does not point to a readable file."
            }
        }
    }
}

tasks.matching {
    it.name == "assembleRelease" ||
        it.name == "bundleRelease" ||
        it.name == "packageRelease"
}.configureEach {
    dependsOn(verifyReleaseSigning)
}

tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs.set(listOf("releaseRuntimeClasspath"))
    includeBomSerialNumber.set(false)
    includeBuildSystem.set(false)
    includeBuildEnvironment.set(false)
    jsonOutput.set(rootProject.layout.projectDirectory.file("audit-output/sbom.json"))
    xmlOutput.set(rootProject.layout.projectDirectory.file("audit-output/sbom.xml"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
