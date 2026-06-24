package dev.kmpilot.todo.data

import dev.kmpilot.todo.auth.LocalAuthScaffold
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The PROOF that the SQLDelight slice persists across an app restart on device. We can't reboot the emulator in a
 * unit test, so we simulate the restart faithfully: build the full local stack (auth + scoped repo) over a TEMP
 * FILE, write data, DROP the whole stack (close the driver), then re-open the SAME file with a brand-new stack —
 * the on-disk DB is all that survives. The reopened app must let the user sign in and still see their owned task.
 *
 * This is exactly what AndroidSqliteDriver/NativeSqliteDriver do on a real device (a "todo.db" file), via the
 * identical commonMain SqlDelightTaskRepository + SqlDelightUserStore + ScopedTaskRepository wiring buildRoot uses.
 */
class RestartPersistenceTest {

    private val fixedNow: () -> LocalDateTime = { LocalDateTime(2026, 1, 1, 0, 0) }
    private val dbFile = File.createTempFile("todo-restart", ".db").also { it.delete() }

    @AfterTest fun cleanup() {
        dbFile.delete()
    }

    @Test
    fun a_user_and_their_owned_task_survive_dropping_and_reopening_the_db_file() = runTest {
        // --- session 1: sign up, add an owned task, then "close the app" (drop the stack) ---
        run {
            val driver = fileBackedDriver(dbFile)
            val db = todoDatabase(driver)
            val auth = LocalAuthScaffold(fixedNow, SqlDelightUserStore(db))
            val repo = ScopedTaskRepository(SqlDelightTaskRepository(db), auth)

            auth.signUp("ann@example.com", "s3cret", "Ann").getOrThrow()
            repo.upsert(Task(id = 0, title = "buy milk"))
            assertEquals(listOf("buy milk"), repo.all().map { it.title })

            driver.close() // app process gone; only the file on disk remains
        }

        // --- session 2: reopen the SAME file with a fresh stack (signed out, as the app boots) ---
        run {
            val driver = fileBackedDriver(dbFile)
            val db = todoDatabase(driver)
            val auth = LocalAuthScaffold(fixedNow, SqlDelightUserStore(db))
            val repo = ScopedTaskRepository(SqlDelightTaskRepository(db), auth)

            // wrong password still fails after the restart (the hash + salt persisted, not plaintext)
            assertTrue(auth.signIn("ann@example.com", "wrong").isFailure, "bad creds must still be rejected")

            // the persisted account can sign back in
            val session = auth.signIn("ann@example.com", "s3cret").getOrThrow()
            assertEquals("Ann", session.displayName)

            // and her owned task is still there, still owner-scoped to her
            val tasks = repo.all()
            assertEquals(listOf("buy milk"), tasks.map { it.title }, "the owned task must survive restart")
            assertEquals(session.userId, tasks.single().ownerId, "the task is still scoped to its owner")

            driver.close()
        }
    }
}
