package dev.kmpilot.todo.data

import dev.kmpilot.todo.auth.LocalAuthScaffold
import dev.kmpilot.todo.data.supabase.SupabaseAuthAdapter
import dev.kmpilot.todo.data.supabase.SupabaseTaskRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The CONFIG-SWITCH proof — ONE selection point ([BackendSelector]) returns EITHER backend behind the SAME
 * `Backend(auth, repo)` shape, with NO app-code change:
 *  - **local** → scaffold auth + the on-device [ScopedTaskRepository] ownership decorator,
 *  - **supabase** → the real Supabase adapters, plain CRUD, ownership enforced SERVER-SIDE by RLS (no decorator).
 *
 * The BEHAVIORAL isolation is proven separately (MultiUserContract = local, SupabaseSwapTest = Supabase over real
 * RLS); this proves the SELECTION wiring. Pure unit test — creating the Supabase client is lazy, so no running stack.
 */
class BackendSwitchTest {

    @Test
    fun config_local_selects_the_scaffold_with_the_on_device_ownership_decorator() {
        val b = BackendSelector.local()
        assertEquals("local-scaffold", b.label)
        assertTrue(b.auth is LocalAuthScaffold, "local backend uses the scaffold auth")
        assertTrue(b.repo is ScopedTaskRepository, "ownership is the on-device decorator locally")
    }

    @Test
    fun config_supabase_selects_the_real_adapters_with_NO_decorator() {
        val b = BackendSelector.supabase()
        assertEquals("supabase", b.label)
        assertTrue(b.auth is SupabaseAuthAdapter, "real backend uses the Supabase auth adapter")
        assertTrue(b.repo is SupabaseTaskRepository, "real backend uses the PostgREST repo")
        assertFalse(b.repo is ScopedTaskRepository, "ownership moved to the SERVER (RLS) — no on-device decorator")
    }
}
