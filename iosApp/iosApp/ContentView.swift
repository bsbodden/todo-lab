import SwiftUI
import TodoApp // the Kotlin/Native framework (baseName in build.gradle.kts)

/// Hosts the shared Compose UI (MainViewController() from iosMain) in SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea(.all)
    }
}
