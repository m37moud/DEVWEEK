import SwiftUI
import DaymarkShared

struct ContentView: UIViewControllerRepresentable {
    let auth: NativeGoogleAuth
    let reminders: ReminderPort
    let widget: WidgetSink
    let widgetSetup: WidgetSetupPort
    let resultNotifications: AttendanceResultPort
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(auth: auth, reminders: reminders, widget: widget, widgetSetup: widgetSetup, resultNotifications: resultNotifications)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) { }
}
