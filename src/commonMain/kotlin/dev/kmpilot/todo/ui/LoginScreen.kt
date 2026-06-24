package dev.kmpilot.todo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.kmpilot.todo.auth.AuthPort
import kotlinx.coroutines.launch

/**
 * The login gate. Email + password with a Sign in / Sign up toggle, plus a "Continue with Google" button that
 * calls the consumed-SERVICE stand-in ([AuthPort.signInWith]). Failures surface inline. New email/password
 * accounts start with an empty task list — the visible proof of per-user isolation.
 */
@Composable
fun LoginScreen(auth: AuthPort) {
    val scope = rememberCoroutineScope()
    var signUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null
        scope.launch {
            val result = if (signUpMode) auth.signUp(email, password, displayName)
            else auth.signIn(email, password)
            result.onFailure { error = it.message ?: "Something went wrong" }
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (signUpMode) "Create account" else "Welcome back", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))

            if (signUpMode) {
                OutlinedTextField(
                    value = displayName, onValueChange = { displayName = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("nameField"),
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emailField"),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("passwordField"),
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("authError"))
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = ::submit,
                enabled = email.isNotBlank() && password.isNotBlank() && (!signUpMode || displayName.isNotBlank()),
                modifier = Modifier.fillMaxWidth().testTag("submit"),
            ) { Text(if (signUpMode) "Sign up" else "Sign in") }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { signUpMode = !signUpMode; error = null }, modifier = Modifier.testTag("toggleMode")) {
                Text(if (signUpMode) "Have an account? Sign in" else "New here? Create an account")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    error = null
                    scope.launch { auth.signInWith("Google").onFailure { error = it.message ?: "Sign-in failed" } }
                },
                modifier = Modifier.fillMaxWidth().testTag("google"),
            ) { Text("Continue with Google") }
        }
    }
}
