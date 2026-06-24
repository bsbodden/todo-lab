import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.11.1"
    id("app.cash.sqldelight") version "2.3.2"
    id("com.android.application") version "8.13.2" // last AGP 8.x — still supports single-module KMP (AGP 9 dropped it)
}

repositories {
    google()
    mavenCentral()
}

// Ktor 3.5 pulls kotlinx-browser 0.5.0, but Compose 1.11 + Decompose 3.5 are built against 0.3, and the
// higher version changes the DOM-interop ABI → ComposeViewport silently fails to attach its canvas (blank
// preview). The preview never exercises Ktor's wasm client (it uses the in-memory repo; the remote adapter
// is JVM-tested via MockEngine), so we pin kotlinx-browser to the version Compose needs.
configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-browser:0.3")
}

sqldelight {
    databases {
        create("TodoDatabase") {
            packageName.set("dev.kmpilot.todo.db")
        }
    }
}

kotlin {
    // wasmJs = the MOBILE preview target (Compose-for-web; the same UI a KMP mobile app renders,
    // viewable in a phone frame with no emulator). jvm() is ONLY a unit-test harness for commonMain.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    jvm()
    androidTarget() // the Android target → APK (buildable on this Linux box with the SDK)
    // iOS targets — the REAL mobile target. Declaring them is fine on Linux; only the compile/link needs macOS.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "TodoApp"; isStatic = true }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("com.arkivanov.decompose:decompose:3.5.0")
                implementation("com.arkivanov.decompose:extensions-compose:3.5.0")
                implementation("io.insert-koin:koin-core:4.2.2")
                implementation("app.cash.sqldelight:runtime:2.3.2")
                implementation("app.cash.sqldelight:coroutines-extensions:2.3.2") // asFlow().mapToList → reactive
                implementation("io.ktor:ktor-client-core:3.5.0")                  // BYOK remote adapter
                implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
                implementation("io.github.nsk90:kstatemachine:0.38.1")            // statechart runtime
                implementation("io.github.nsk90:kstatemachine-coroutines:0.38.1") // Flow-native active-state
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
                implementation("io.ktor:ktor-client-mock:3.5.0") // fake REST backend for the remote-adapter contract test
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                // MUST match what Compose 1.11 + Decompose 3.5 are built against (0.3). Forcing a newer
                // kotlinx-browser changes the DOM-interop ABI → ComposeViewport silently fails to attach
                // its canvas (blank preview, no error). Verified the hard way.
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.xerial:sqlite-jdbc:3.47.1.0")   // local-SQL adapter (JVM test harness)
                implementation("app.cash.sqldelight:sqlite-driver:2.3.2") // JdbcSqliteDriver for the SQLDelight adapter
                implementation(compose.desktop.currentOs)
                // The REAL backend-SWAP adapter (jvm-only proof; off wasm/android/ios to dodge the ktor-wasm/
                // kotlinx-browser pins). supabase-kt 3.6.0 is built against ktor 3.4.3 + kotlinx-serialization 1.11.0
                // (the version commonMain already uses); ktor 3.5.0 (already on the classpath via ktor-client-core) is
                // binary-compatible, so the CIO engine is pinned to 3.5.0 to match the resolved core.
                implementation("io.github.jan-tennert.supabase:auth-kt:3.6.0")      // GoTrue → SupabaseAuthAdapter
                implementation("io.github.jan-tennert.supabase:postgrest-kt:3.6.0") // PostgREST → SupabaseTaskRepository
                implementation("io.github.jan-tennert.supabase:realtime-kt:3.6.0")  // Postgres-Changes WS → observeAll() realtime
                // Realtime is a WebSocket; the CIO engine speaks WS, but supabase-kt's Realtime is most reliable on
                // OkHttp (its reference engine). Use OkHttp here so the cross-client push proof doesn't flake on WS frames.
                implementation("io.ktor:ktor-client-okhttp:3.5.0")                  // WS-capable jvm Ktor engine for Realtime
                implementation("io.ktor:ktor-client-cio:3.5.0")                     // jvm Ktor engine for supabase-kt
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.10.1")
                implementation("app.cash.sqldelight:android-driver:2.3.2") // AndroidSqliteDriver → on-device persistence
            }
        }
        // The default KMP hierarchy template synthesizes iosMain (shared by iosArm64 + iosSimulatorArm64); it is
        // created lazily, so reach it via invoke {} rather than `by getting` (which evaluates too early).
        iosMain {
            dependencies {
                implementation("app.cash.sqldelight:native-driver:2.3.2") // NativeSqliteDriver → on-device persistence
            }
        }
    }
}

android {
    namespace = "dev.kmpilot.todo"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.kmpilot.todo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

compose.desktop {
    application {
        mainClass = "dev.kmpilot.todo.MainKt"
    }
}
