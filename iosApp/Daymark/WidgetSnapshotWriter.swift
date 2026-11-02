import Foundation
import WidgetKit
import DaymarkShared

final class WidgetSnapshotWriter: NSObject, WidgetSink {
    private let groupIdentifier: String
    init(groupIdentifier: String = Bundle.main.object(forInfoDictionaryKey: "DAYMARK_APP_GROUP_IDENTIFIER") as? String ?? "") {
        self.groupIdentifier = groupIdentifier
    }
    private var file: URL? {
        guard !groupIdentifier.isEmpty, !groupIdentifier.contains("$(") else { return nil }
        return FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupIdentifier)?.appendingPathComponent("DaymarkWidget", isDirectory: true).appendingPathComponent("daymark-widget.json")
    }
    func publish(json: String) -> Bool {
        guard let file, let snapshot = NativeWidgetSnapshot.decode(Data(json.utf8)) else { return false }
        do {
            // Encode recognized display fields only; unknown input cannot persist a token or database path.
            let data = try JSONEncoder().encode(snapshot)
            let folder = file.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true,
                attributes: [.protectionKey: FileProtectionType.complete])
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            var protectedFolder = folder; try protectedFolder.setResourceValues(values)
            try data.write(to: file, options: [.atomic, .completeFileProtection])
            WidgetCenter.shared.reloadTimelines(ofKind: "DaymarkAttendance")
            return true
        } catch { return false }
    }
    func clear() -> Bool {
        guard let file else { return false }
        do {
            if FileManager.default.fileExists(atPath: file.path) { try FileManager.default.removeItem(at: file) }
            WidgetCenter.shared.reloadTimelines(ofKind: "DaymarkAttendance")
            return true
        } catch { return false }
    }
}
