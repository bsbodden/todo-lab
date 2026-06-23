package dev.kmpilot.todo.data

import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reusable repository CONTRACT. Every adapter (in-memory, SQLite, Supabase, …) must satisfy it,
 * so the same assertions prove the backends are interchangeable — the heart of the BYOK design.
 */
suspend fun assertRepositoryContract(repo: TaskRepository) {
    // insert assigns an id and the row is retrievable
    val created = repo.upsert(Task(id = 0, title = "Buy milk"))
    assertTrue(created.id > 0, "insert must assign a positive id")
    assertEquals("Buy milk", repo.byId(created.id)?.title)
    assertEquals(1, repo.observeAll().first().size)

    // a second insert gets a distinct id
    val second = repo.upsert(Task(id = 0, title = "Walk dog"))
    assertTrue(second.id != created.id)
    assertEquals(2, repo.all().size)

    // update mutates in place (no new row)
    repo.upsert(created.copy(title = "Buy oat milk", isCompleted = true))
    assertEquals("Buy oat milk", repo.byId(created.id)?.title)
    assertTrue(repo.byId(created.id)!!.isCompleted)
    assertEquals(2, repo.all().size)

    // delete removes only that row
    repo.delete(created.id)
    assertNull(repo.byId(created.id))
    assertEquals(1, repo.all().size)
    assertEquals(second.id, repo.all().single().id)
}

/**
 * The PAGING contract — every adapter must page identically whether it uses the default client-side impl
 * (in-memory, remote) or a DB-pushed override (SQLDelight). Proves the cursor tiles the full set with no
 * overlap or gap, and that [Page.hasMore] flips exactly on the last page.
 */
suspend fun assertPagingContract(repo: TaskRepository) {
    repeat(5) { i -> repo.upsert(Task(id = 0, title = "T$i")) }
    val ids = repo.all().sortedBy { it.id }.map { it.id }
    assertEquals(5, ids.size, "fixture must seed 5 rows")

    val p1 = repo.page(PageRequest(afterId = null, limit = 2))
    assertEquals(ids.subList(0, 2), p1.items.map { it.id })
    assertTrue(p1.hasMore)

    val p2 = repo.page(PageRequest(afterId = p1.nextCursor, limit = 2))
    assertEquals(ids.subList(2, 4), p2.items.map { it.id })
    assertTrue(p2.hasMore)

    val p3 = repo.page(PageRequest(afterId = p2.nextCursor, limit = 2))
    assertEquals(ids.subList(4, 5), p3.items.map { it.id })
    assertFalse(p3.hasMore, "last page must report no more")

    // the three pages tile the full ordered set with no overlap or gap
    assertEquals(ids, (p1.items + p2.items + p3.items).map { it.id })

    // a page past the end is empty
    val past = repo.page(PageRequest(afterId = ids.last(), limit = 2))
    assertTrue(past.items.isEmpty() && !past.hasMore)
}
