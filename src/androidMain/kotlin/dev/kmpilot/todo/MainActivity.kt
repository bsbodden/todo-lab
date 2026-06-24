package dev.kmpilot.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.kmpilot.todo.data.AppContextHolder
import dev.kmpilot.todo.ui.App
import dev.kmpilot.todo.ui.buildRoot

/** Android entrypoint — hosts the shared App() in a ComponentActivity. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seed the app Context BEFORE buildRoot so todoDriverOrNull() can build the AndroidSqliteDriver → tasks
        // + users persist across restarts. Application context (not the activity) to avoid leaking the activity.
        AppContextHolder.appContext = applicationContext
        setContent { App(buildRoot()) }
    }
}
