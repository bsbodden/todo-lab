package dev.kmpilot.todo.auth

/**
 * SCAFFOLDING-GRADE salted password hashing — pure Kotlin, multiplatform, zero deps.
 *
 * It does the two things the scaffold needs to be honest: it is **salted** (the per-account salt = the email, so
 * identical passwords across accounts produce different digests) and it **never stores plaintext**. That's where
 * the fidelity ends — this is a fast non-cryptographic mixer (FNV-1a-style, iterated), NOT a password KDF.
 *
 * A real backend hashes server-side with a slow, memory-hard KDF (bcrypt / scrypt / argon2) and a random per-user
 * salt. That is a "your backend" concern (the secret + the authoritative check live server-side) — deferred. Do
 * not ship this.
 */
internal object ScaffoldPasswordHash {

    fun hash(password: String, salt: String): String {
        val seasoned = "$salt::$password"
        // Iterate to make the digest depend on the whole input order; still trivially fast (NOT a real KDF).
        var h = 0xcbf29ce484222325uL
        repeat(1000) { round ->
            for (c in seasoned) {
                h = h xor (c.code.toULong() + round.toULong())
                h *= 0x100000001b3uL
            }
        }
        return h.toString(16)
    }

    fun verify(password: String, salt: String, expected: String): Boolean =
        hash(password, salt) == expected
}
