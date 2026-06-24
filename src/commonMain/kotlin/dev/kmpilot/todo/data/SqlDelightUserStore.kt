package dev.kmpilot.todo.data

import dev.kmpilot.todo.auth.StoredUser
import dev.kmpilot.todo.auth.UserStore
import dev.kmpilot.todo.db.TodoDatabase
import dev.kmpilot.todo.db.UserEntity

/**
 * The DURABLE local-auth user store — persists [LocalAuthScaffold][dev.kmpilot.todo.auth.LocalAuthScaffold]'s
 * accounts to the [UserEntity] table so they survive app restarts on device. Same [UserStore] port as the
 * in-memory store; only where the rows live changes. UserEntity has no custom column types, so it needs no
 * adapter wiring (unlike TaskEntity — see [todoDatabase]).
 */
class SqlDelightUserStore(db: TodoDatabase) : UserStore {
    private val queries = db.userQueries

    override fun create(user: StoredUser) {
        queries.insertUser(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            passwordHash = user.passwordHash,
            salt = user.salt,
        )
    }

    override fun findByEmail(email: String): StoredUser? =
        queries.selectByEmail(email).executeAsOneOrNull()?.toStored()

    override fun findById(id: String): StoredUser? =
        queries.selectById(id).executeAsOneOrNull()?.toStored()

    override fun all(): List<StoredUser> = queries.selectAll().executeAsList().map { it.toStored() }
}

private fun UserEntity.toStored() = StoredUser(
    id = id,
    email = email,
    displayName = displayName,
    passwordHash = passwordHash,
    salt = salt,
)
