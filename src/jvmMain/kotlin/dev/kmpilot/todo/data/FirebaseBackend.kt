package dev.kmpilot.todo.data

import dev.kmpilot.todo.data.firebase.FirebaseAuthAdapter
import dev.kmpilot.todo.data.firebase.FirebaseBootstrap
import dev.kmpilot.todo.data.firebase.FirebaseTaskRepository

/**
 * The jvm-ONLY Firebase arm of the backend SWAP.
 *
 * [BackendSelector] itself lives in `nonWasmMain` so the SAME selector compiles on Android + iOS + jvm, where it
 * already exposes `local()` and `supabase()` — both of which are multiplatform (supabase-kt links into the iOS
 * framework and the Android APK). Firebase is different: the GitLive firebase-kotlin-sdk runs on JVM via the pure-Java
 * firebase-java-sdk, but its DEVICE story needs the cocoapods iOS side — a documented FOLLOW-UP. So the Firebase
 * factory cannot live in `nonWasmMain` without breaking the iOS link. It lives here, in `jvmMain`, as an extension on
 * [BackendSelector], and is reachable only from the jvm runner + the jvm conformance test.
 *
 * Same ports, same isolation guarantee as `supabase()`; the on-device [ScopedTaskRepository] decorator is GONE —
 * [FirebaseTaskRepository] constrains its queries and Firestore security RULES enforce ownership server-side.
 */
fun BackendSelector.firebase(): Backend {
    FirebaseBootstrap.ensure() // idempotent: init the SDK + point Auth/Firestore at the local emulators
    return Backend("firebase", FirebaseAuthAdapter(), FirebaseTaskRepository())
}
