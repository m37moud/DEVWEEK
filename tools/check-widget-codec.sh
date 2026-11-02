#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
codec_tmp="$(mktemp -d "${TMPDIR:-/tmp}/daymark-widget-codec.XXXXXX")"
trap 'rm -rf -- "$codec_tmp"' EXIT
cat > "$codec_tmp/main.swift" <<'SWIFT'
import Foundation
try WidgetSnapshotFixtures.roundTrips()
WidgetSnapshotFixtures.rejectsUntrustedPayloads()
WidgetSnapshotFixtures.staleSnapshotIsNotTodaysConfirmation()
let catalog = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))) as! [String: Any]
let strings = catalog["strings"] as! [String: Any]
for language in ["en", "ar"] {
    for key in ["widget_updated", "widget_deadline"] {
        let item = strings[key] as! [String: Any]
        let translations = item["localizations"] as! [String: Any]
        let translated = translations[language] as! [String: Any]
        let unit = translated["stringUnit"] as! [String: Any]
        let format = unit["value"] as! String
        precondition(format.contains("%1$@"))
        let rendered = String(format: format, "٠٩:٠٥")
        precondition(rendered.contains("٠٩:٠٥"))
    }
}
print("Widget codec and EN/AR native format templates passed on macOS; iOS host/UI remains unverified.")
SWIFT
xcrun swiftc -module-cache-path "$codec_tmp/ModuleCache" \
    "$repo_root/iosApp/DaymarkWidget/WidgetSnapshot.swift" \
    "$repo_root/iosApp/DaymarkTests/WidgetSnapshotFixtures.swift" \
    "$codec_tmp/main.swift" -o "$codec_tmp/check"
"$codec_tmp/check" "$repo_root/iosApp/DaymarkWidget/Localizable.xcstrings"
