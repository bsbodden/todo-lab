package dev.kmpilot.todo

import androidx.compose.ui.window.ComposeUIViewController
import dev.kmpilot.todo.ui.App
import dev.kmpilot.todo.ui.buildRoot
import platform.UIKit.UIViewController

/** iOS entrypoint — the iosApp Xcode project hosts this in a SwiftUI UIViewControllerRepresentable. */
fun MainViewController(): UIViewController = ComposeUIViewController { App(buildRoot()) }
