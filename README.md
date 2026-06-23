# todo-lab — a clean KMPilot-stack reimplementation of Alkaa's core (test-first)

A **lab** that rebuilds the meat of [Alkaa](https://github.com/igorescodro/alkaa) (a to-do app) on KMPilot's **opinionated house stack**, **test-first**, and along the way extracts the reusable **framework-library** components — centered on the one this task is about: a **swappable persistence adapter** (local SQL ⇄ remote backend) behind a single DDD port.

> Scope note: the app runs as a **mobile preview** — the `commonMain` Compose UI compiled to **`wasmJs`** and rendered in a phone frame in the browser (the KMPilot **Tier-1 preview**). This is the *same* `commonMain` a full KMP app compiles for `android()`/`iosX64()` — no Android SDK, no emulator, no Xcode required to see and tap the real mobile UI. The `jvm()` target is kept **only as a unit-test harness** for `commonMain` logic (it is *not* the thing you launch to test). Wiring is via **Koin** (both entry points resolve from the container); the SQLDelight `.sq` schema is the next slice.

## The selected stack (launch defaults)
Scored on **UCD + DDD + Test-first** alignment, balanced with **popularity + maturity** (see `kmpilot-launch-priorities` memory).

| Concern | Default | Curated alt (later) |
|---|---|---|
| UI | Compose Multiplatform + Material 3 | — |
| State / presentation | MVI (StateFlow + one-shot effects) | Orbit-MVI |
| Navigation | Decompose (externalized, testable nav state) | navigation-compose |
| DI | Koin | kotlin-inject / Metro |
| **Persistence (local default)** | **SQLDelight** (real `.sq` schema, runs here) · raw-JDBC adapter = reference alt | Room-KMP (22% of corpus) |
| **Remote / BYOK** | Ktor + kotlinx.serialization | — |
| Time | kotlinx-datetime | — |
| Errors | Result / Arrow | — |
| Tests | kotlin.test + Kotest + Turbine | — |

## The framework-library components this lab produced
These graduate into the cartridge:
- **`data/TaskRepository`** — the DDD repository **port** (the BYOK seam). The app depends only on this.
- **`data/InMemoryTaskRepository`** — the test/preview adapter + the "simulate accumulated state" fixture.
- **`data/SqlDelightTaskRepository`** — the **opinionated local-SQL default**: a real SQLDelight `.sq` schema (`src/commonMain/sqldelight/`) → generated type-safe CRUD, `asFlow().mapToList` reactive streaming, and column adapters (LocalDateTime, AlarmInterval) wired at the boundary so the domain stays framework-free. Same `commonMain` code compiles for android/ios; only the `SqlDriver` is platform-specific (`jvmSqliteDriver` here).
- **`data/SqliteTaskRepository`** (JVM) — the hand-rolled raw-JDBC adapter, kept as a **reference alternate** (superseded as default by SQLDelight).
- **`data/remote/{BackendClient, RestBackendClient, RemoteTaskRepository, Backends}`** — the **BYOK remote** seam: a `TaskDto` + a **real Ktor REST `BackendClient`** (`RestBackendClient`: GET/POST/PUT/DELETE JSON, Long ids) mapped to the domain port by the backend-agnostic `RemoteTaskRepository` (with a local cache). **Tested over real JSON-on-HTTP** via a `MockEngine` backend (`RemoteTaskRepositoryTest`). Supabase/PocketBase/Firebase remain documented auth/url variants of the same shape; one-line `taskRepository(backend)` swap.
- **`commonTest/data/assertRepositoryContract`** — the reusable **adapter conformance test** (every backend must pass it).
- **`di/AppModule`** — the **Koin** wiring: `appModule` (cross-cutting singletons, incl. the injectable `Now` clock seam) + `inMemoryPersistenceModule(seed)`. **The BYOK persistence swap is one Koin module** — the preview binds in-memory, the jvm binds `jvmSqlitePersistenceModule` (real SQLite); the rest of the app is identical. A future Supabase/Firebase backend is just another persistence module.
- **`domain/Recurrence`** — a reusable, calendar-aware **recurrence engine** (the timezone/catch-up logic).
- **`ui/*` + `presentation/Components.kt`** — pure Compose Material 3 screens (state-in / callbacks-out → headless-testable) + **Decompose** nav components (externalized, testable nav state; Task List → Add → Detail).

## The BYOK persistence story (the point)
The app/use-cases touch **only `TaskRepository`**. Swapping the backend is one binding:
```kotlin
// local-first default (prod: SQLDelight; lab: JDBC-SQLite)
val repo: TaskRepository = SqliteTaskRepository()
// …or bring your own backend — the rest of the app is unchanged:
val repo: TaskRepository = taskRepository(Backend.SUPABASE,  mapOf("url" to "…", "anonKey" to "…"))
val repo: TaskRepository = taskRepository(Backend.POCKETBASE, mapOf("baseUrl" to "…"))
val repo: TaskRepository = taskRepository(Backend.FIREBASE,  mapOf("projectId" to "…", "apiKey" to "…"))
```
**Proof it's real, not aspirational:** the in-memory adapter **and** the real SQLite adapter pass the *same* `assertRepositoryContract` — `InMemoryTaskRepositoryTest` (commonTest) and `SqliteTaskRepositoryTest` (jvmTest). A new backend is "done" when it passes that contract.

## Test-first evidence
- **RED→GREEN was demonstrated explicitly on `Recurrence`** — the test was written first, run RED (`NotImplementedError`), then the minimal implementation took it GREEN. (The other domain rules are written to the same tested contract — the tests are the spec.)
- The domain invariants mirror Alkaa's original unit tests: recurrence advance + **5h-overdue catch-up** + **calendar-aware month clamping** (`ScheduleNextAlarmTest`), search substring/empty-all/order (`SearchTasksTest`), the 30-day tracker window + percentages-sum-to-1 (`LoadCompletedTasksByPeriodTest`), complete/uncomplete `completedDate` toggling.
- **29 tests, all green** on the JVM — domain + persistence + DI graph + **statechart** + **headless Compose UI tests** (`runComposeUiTest`: each of the four screen states renders, the right checkbox toggles, retry fires). **Four persistence adapters — in-memory, raw-JDBC, SQLDelight, and the Ktor remote/BYOK adapter — pass the *same* `assertRepositoryContract`** (the remote one over real JSON-on-HTTP via MockEngine), proving they're interchangeable (the BYOK guarantee). A separate **`assertPagingContract`** proves the keyset pager tiles identically across the default client-side path and the SQLDelight DB-pushdown override. **`TaskListMachineTest`** drives the screen statechart through Loading → Content/Empty/Error/Retry and asserts illegal transitions are no-ops.
- **A real bug was caught making it runnable:** Decompose's `childStack` must be created on the AWT event thread, not the bare `main` thread (`NotOnMainThreadException`) — fixed in `Main.kt:onEventThread`.

## Toolchain (current)
**Gradle 9.6 (wrapper) · Kotlin 2.4.0 · Compose Multiplatform 1.11.1 · JDK 21 LTS** (the KMP/Android-recommended LTS;
run Gradle on it via sdkman). Libraries all latest: SQLDelight 2.3.2 · Ktor 3.5.0 · KStateMachine 0.38.1 · Decompose
3.5.0 · Koin 4.2.2 · coroutines 1.11.0 · datetime 0.7.1 · serialization 1.11.0.
> Two wasm gotchas baked into the build: **(1)** Ktor 3.5 pulls `kotlinx-browser:0.5` but Compose 1.11 needs `0.3` — the
> higher version silently breaks `ComposeViewport`'s canvas, so it's `force`d to 0.3 (the preview never uses Ktor at
> runtime). **(2)** Compose 1.11 renders the canvas inside a **shadow root**, so `index.html`'s loading-overlay logic
> pierces shadow roots to detect it.

## Run it — the mobile preview (the point)
```bash
cd lab/todo-lab
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.6-zulu"   # JDK 21 LTS

# 1. build the wasmJs mobile bundle (the same commonMain UI an Android/iOS app renders)
./gradlew wasmJsBrowserDevelopmentExecutableDistribution --console=plain

# 2. serve it (the .wasm MIME type matters — a plain http.server serves octet-stream and it won't load)
python3 ../../docs/kmp-corpus/serve-wasm.py 8082 build/dist/wasmJs/developmentExecutable

# 3. open http://127.0.0.1:8082/index.html  → the app in a phone frame, with a supported-device toggle
```
`src/wasmJsMain/.../main.kt` is the preview entry: it builds the Decompose `RootComponent` on the browser
UI thread (`Dispatchers.Main`) with the **in-memory** persistence adapter, and renders **full-window into
`document.body`** (`resources/app.html`). Nav-on-the-UI-thread is satisfied for free because the browser is
single-threaded.

The phone frame is the **parent page** (`resources/index.html`): it `<iframe>`s `app.html` and sizes that
iframe to the selected device. **Why full-window + iframe, not a sized sub-element:** Compose/Skiko paints
**blank on Chrome** when `ComposeViewport` targets a fixed-size sub-element (verified — blank in real Chrome,
fine in Firefox). The full-window `document.body` overload is JetBrains' own known-good pattern (KotlinConf
app); the iframe just constrains its viewport to a phone. Do a **hard refresh** (Ctrl+Shift+R) after a
rebuild — the `.wasm` filename is content-hashed and `todo-lab.js` is cached by name.

## The real mobile targets — Android + iOS
This app is now a **true multiplatform** app: the same `commonMain` Compose UI compiles and runs natively on
**Android** and **iOS**, with the **wasm** build kept as the in-browser preview/emulator. Each platform has a thin
entrypoint that calls the shared `ui/App.kt` `App(buildRoot())`:
- **Android** — `MainActivity` (`setContent { App(buildRoot()) }`); `./gradlew assembleDebug` → APK (needs the Android SDK).
- **iOS** — `MainViewController()` hosted by the SwiftUI `iosApp/` (an XcodeGen `project.yml`; the `.xcodeproj` is
  generated in CI). Build on a Mac, or **free on CI** via the **Actions → "iOS (free simulator build)"** workflow
  (`.github/workflows/ios.yml`) — it compiles an iOS **Simulator** `.app` on a GitHub-hosted macOS runner (no signing)
  and uploads it as an artifact (add an `APPETIZE_TOKEN` secret to stream a browser link).

> **Mobile persistence (least-risk launch):** `buildRoot()` binds the **in-memory** `TaskRepository` (seeded with the
> same default tasks as the wasm preview) so the task list works immediately on Android + iOS. The SQLDelight local-SQL
> default is JVM-tested; swapping a platform `SqlDriver` (`NativeSqliteDriver` on iOS, `AndroidSqliteDriver` on Android)
> behind the same DI seam is the next slice — the rest of the app is unchanged.

## Test harness (commonMain logic only — not a way to "run the app")
```bash
./gradlew jvmTest --console=plain  # 29 tests: domain + persistence (4 adapters, +paging) + DI + statechart + headless Compose UI
./gradlew run                      # OPTIONAL desktop window — a debugging harness, NOT the product form factor
```

## What's stubbed / next
- **androidx Paging UI wrapper** — the port now exposes a wasm-safe keyset-paging *primitive* (`page(PageRequest): Page<Task>`); the presentation layer (android/ios/jvm, NOT wasm) wraps it with `androidx.paging` `Pager`/`PagingSource` (SQLDelight ships `QueryPagingSource`) for `LazyColumn` infinite scroll. Kept out of `commonMain` so the preview stays wasm-safe.
- **Backend-specific remote clients** — the generic `RestBackendClient` is real and tested; the Supabase (apikey header + PostgREST `?id=eq.` filters + `Prefer: resolution=merge-duplicates`), PocketBase (string ids), and Firestore variants remain documented skeletons. Per `docs/research/10` the opinionated default topology is **server-authoritative + local-cache** (which `RemoteTaskRepository` already models), with offline-first an opt-in upgrade.
- A **Koin remote module** + a platform `HttpClient` engine (Darwin/OkHttp/CIO) to make the BYOK swap runnable on device (the lab tests inject a MockEngine).
- The `@Screen`/`@Transition` **minimap metadata** the generator will emit.

**Device-level model-based testing (the test-first payoff):** the screen is a statechart, so its events ARE the
user interactions. `runtime/ScreenBridge` (expect/actual) publishes the active screen's **logical state** to
`globalThis.__screen` on wasm (no-op elsewhere). `preview-tests/model-based-ui.mjs` then drives the **real
wasm preview with taps** and asserts the *statechart's state* (not pixels): toggling each task walks the chart
`Content:3 → Content:2 → Content:1 → Empty`.

**Scenarios / preconditions (the "see it in the frame" loop).** The app boots into a named precondition from
`?scenario=NAME` — selectable via the frame's **Scenario dropdown** or `globalThis.__setScenario(name)`. Each is
an LLM-authorable fixture (+ entry state): `default` (3 tasks → Content), `empty` (→ Empty), `many` (25 → Content),
`error` (`FailingTaskRepository` → **Error/Retry**, a state a healthy repo can never reach). `preview-tests/scenarios.mjs`
verifies each boots into the expected statechart state. **Run all device tests with one command —
`./preview-tests/run.sh`** (build → serve → test → teardown); details in `preview-tests/README.md`. (Next:
the live `fire(event)` write half.)

**Done since:** ✅ Koin DI wired through both entry points (graph verified by `AppModuleTest`). ✅ **SQLDelight `.sq` schema is the local default** — generated type-safe CRUD + reactive `Flow` + column adapters, behind the same `TaskRepository` port and Koin binding; passes the shared `assertRepositoryContract`. ✅ **Keyset pagination** — a wasm-safe `Page`/`PageRequest` primitive with a default client-side impl + a SQLDelight DB-pushdown override, both proven identical by `assertPagingContract`. ✅ **Ktor remote/BYOK adapter** — a real `RestBackendClient` (JSON REST) behind `RemoteTaskRepository`, passing the shared CRUD + paging contracts over real JSON-on-HTTP via a MockEngine backend. All four adapters compile to wasm without breaking the preview (verified: render frac 0.0353, 0 errors).
