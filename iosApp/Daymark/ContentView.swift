import SwiftUI
import DaymarkShared

struct ContentView: UIViewControllerRepresentable {
    let auth: NativeGoogleAuth
    let reminders: ReminderPort
    let widget: WidgetSink
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(auth: auth, reminders: reminders, widget: widget)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) { }
}
