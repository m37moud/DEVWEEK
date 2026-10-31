import SwiftUI
import DaymarkShared

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
#if DEBUG
        NativeProbeViewControllerKt.NativeProbeViewController()
#else
        MainViewControllerKt.MainViewController()
#endif
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) { }
}
