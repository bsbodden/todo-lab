package dev.kmpilot.todo.data

/**
 * Keyset (cursor) pagination request. Keyset > offset because it stays O(1) as the table grows and maps
 * cleanly onto remote backends (PostgREST range, Firestore startAfter) — the community default. `afterId`
 * null = first page; otherwise return rows whose id is strictly greater than the cursor.
 */
data class PageRequest(val afterId: Long? = null, val limit: Int = 20)

/**
 * One page of results. [nextCursor] is the cursor to pass as the next [PageRequest.afterId] (the last item's
 * id, or null when empty); [hasMore] signals whether another page exists. This is precisely the shape an
 * androidx `PagingSource.load()` consumes at the UI edge — but it stays framework-free so it compiles to wasm.
 */
data class Page<T>(val items: List<T>, val nextCursor: Long?, val hasMore: Boolean)
