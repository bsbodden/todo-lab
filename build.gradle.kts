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

    // Insert an intermediate `nonWasmMain`/`nonWasmTest` group into the DEFAULT hierarchy so it carries the REAL
    // backend-SWAP adapter (supabase-kt) to the DEVICE targets (Android + iOS + jvm) but NOT to wasmJs. Using the
    // template (instead of manual `dependsOn` edges) keeps the rest of the default hierarchy intact — in particular
    // it still synthesizes `iosMain` (shared by iosArm64+iosSimulatorArm64) and the `compileIosMainKotlinMetadata`
    // task. wasmJs is simply left out of the group, so neither supabase-kt nor a Ktor engine ever reaches the wasm
    // classpath (the entire point of the intermediate set — and why the kotlinx-browser 0.3 pin stays clean).
    applyDefaultHierarchyTemplate {
        common {
            group("nonWasm") {
                withJvm()
                withAndroidTarget()
                withIos()
            }
        }
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
        // nonWasmMain is materialized by the custom hierarchy group above (commonMain ← nonWasmMain ← {android, ios,
        // jvm}); it holds the REAL backend-SWAP adapter sources. supabase-kt 3.6.0 declares ktor 3.4.3; commonMain
        // already pins ktor-client-core 3.5.0, so Gradle's newest-wins resolves the WHOLE classpath to ktor 3.5.0
        // (binary-compatible, and — crucially — past the ktor 3.2.1 fix for the D8 "Space characters in SimpleName"
        // bug → the APK dexes cleanly at minSdk 24). The WS-capable engine is supplied PER PLATFORM below.
        val nonWasmMain by getting {
            dependencies {
                implementation("io.github.jan-tennert.supabase:auth-kt:3.6.0")      // GoTrue → SupabaseAuthAdapter
                implementation("io.github.jan-tennert.supabase:postgrest-kt:3.6.0") // PostgREST → SupabaseTaskRepository
                implementation("io.github.jan-tennert.supabase:realtime-kt:3.6.0")  // Postgres-Changes WS → observeAll() realtime
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.xerial:sqlite-jdbc:3.47.1.0")   // local-SQL adapter (JVM test harness)
                implementation("app.cash.sqldelight:sqlite-driver:2.3.2") // JdbcSqliteDriver for the SQLDelight adapter
                implementation(compose.desktop.currentOs)
                // WS-capable jvm Ktor engine for supabase-kt's Realtime (OkHttp is supabase-kt's reference engine;
                // it matches Android and speaks WS reliably, so the cross-client push proof doesn't flake on WS frames).
                implementation("io.ktor:ktor-client-okhttp:3.5.0")
                // SECOND real backend, jvm-ONLY (the GitLive firebase-kotlin-sdk conformance proof). On JVM the
                // `-jvm` artifacts forward to dev.gitlive:firebase-java-sdk (a pure-Java port — no Play-services /
                // native binaries), so Firebase Auth + Firestore run against the local Emulator Suite with no Android
                // Context and no Mac. This stays in jvmMain (NOT nonWasmMain) because device-multiplatform Firebase
                // needs the cocoapods iOS side — a documented FOLLOW-UP. Both base coords resolve their `-jvm` variant
                // and transitively bring firebase-java-sdk:0.6.3 + kotlinx-coroutines-play-services.
                implementation("dev.gitlive:firebase-auth:2.4.0")      // GitLive Auth → FirebaseAuthAdapter
                implementation("dev.gitlive:firebase-firestore:2.4.0") // GitLive Firestore → FirebaseTaskRepository
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.10.1")
                implementation("app.cash.sqldelight:android-driver:2.3.2") // AndroidSqliteDriver → on-device persistence
                implementation("io.ktor:ktor-client-okhttp:3.5.0")         // WS-capable Android Ktor engine (OkHttp WS)
            }
        }
        // iosMain is shared by iosArm64 + iosSimulatorArm64 and now dependsOn(nonWasmMain) via the custom group. The
        // default hierarchy template creates it LAZILY (after this block body runs), so it is not reachable via
        // `by getting`/`named()` here — configure it via the live `all {}` hook that fires when it materializes.
        // (Same gotcha-proof pattern food-lab uses for its late iosMain config.)
        all {
            if (name == "iosMain") {
                dependencies {
                    implementation("app.cash.sqldelight:native-driver:2.3.2") // NativeSqliteDriver → on-device persistence
                    implementation("io.ktor:ktor-client-darwin:3.5.0")        // WS-capable Kotlin/Native Ktor engine (NSURLSession)
                }
            }
        }
    }
}

android {
    namespace = "dev.kmpilot.todo"
    // compileSdk 36: supabase-auth-kt pulls androidx.browser:1.10.0 (Custom Tabs, for OAuth) transitively into the
    // APK now that the Supabase adapter is on androidMain; that AAR requires compiling against API 36. This is a
    // COMPILE-time SDK only — minSdk stays 24 (no device-support change) and targetSdk stays 35 (no runtime-behavior
    // opt-in). food-lab already compiles against 36 for the same reason. (This is unrelated to the ktor/D8 DEX path.)
    compileSdk = 36
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
