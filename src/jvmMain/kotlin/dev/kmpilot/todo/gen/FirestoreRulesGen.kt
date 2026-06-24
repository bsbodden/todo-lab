package dev.kmpilot.todo.gen

/**
 * GENERATOR PoC — emits the **Firestore security rules** for an [EntityDescriptor] from the SAME ownership policy that
 * drives the Supabase RLS (the "one policy, many targets" thesis). Reproduces the committed, end-to-end-verified
 * `firestore.rules` (proven by [SchemaGenTest]).
 *
 * Contrast vs Postgres RLS (which auto-filters): Firestore rules VALIDATE a query, so the adapter must constrain its
 * reads to its own docs. The `resource != null` guard makes a missing/foreign doc a clean DENY (not an evaluation error).
 */
object FirestoreRulesGen {

    fun rules(d: EntityDescriptor): String {
        val owner = d.ownerField                          // Firestore stores the field verbatim (camelCase)
        val docId = "${d.name.replaceFirstChar { it.lowercase() }}Id"   // e.g. taskId
        val signedInOwns = "request.auth != null && resource != null && resource.data.$owner == request.auth.uid"
        val createOwns = "request.auth != null && request.resource.data.$owner == request.auth.uid"

        return buildString {
            appendLine("rules_version = '2';")
            appendLine("service cloud.firestore {")
            appendLine("  match /databases/{database}/documents {")
            appendLine()
            appendLine("    // GENERATED from the domain entity '${d.name}' — owner-private by '$owner' (== the Supabase RLS rule).")
            appendLine("    match /${d.collection}/{$docId} {")
            appendLine("      allow read:           if $signedInOwns;")
            appendLine("      allow create:         if $createOwns;")
            appendLine("      allow update, delete: if $signedInOwns;")
            appendLine("    }")
            appendLine("  }")
            appendLine("}")
        }
    }
}
