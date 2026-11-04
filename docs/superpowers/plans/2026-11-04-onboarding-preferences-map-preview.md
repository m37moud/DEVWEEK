# Onboarding preferences and registered-place preview

## Approved change

The user approved implementation in the existing workspace on 2026-11-04.
Language and appearance selection move out of onboarding and remain available
through the existing Settings screen. This updates the language-selection step
in frozen v1 section 5 without modifying the frozen reference. Fresh installations
continue to follow system preferences; existing saved preferences remain intact.

Keep the current Welcome → Profile → Place → Schedule → Review flow, draft
recovery/reset, edit/back actions, and Today/Customize completion destinations.
Remove onboarding-only preference state and the inactive top-bar shortcuts.

## Preview contract

On the Place step, render the persisted registered position automatically using
MapLibre Compose 0.20.0 and OpenFreeMap. Show a fixed pin and attendance radius;
changing radius updates the circle. Map gestures, pin editing, camera animation,
and device-location tracking are disabled. Registration validation, persistence,
and attendance evaluation do not change.

A failed recapture preserves the prior saved place. Loading tiles must not block
onboarding. Style failure or a 15-second initial-render timeout shows a localized
fallback and map-only retry; retry never requests GPS. Coordinate text remains
available independently of map loading. Positions outside Web Mercator latitude
coverage use the text fallback rather than displaying a different position.

Render marker/radius locally; disclose the viewed-area requests and retain map
attribution/privacy links. Disable map-engine diagnostics because they may include
coordinates or resource URLs. Use Liberty/Dark base styles with the app theme.

Map dependencies live in `mobileMain`, shared by Android and iOS. JVM uses text-only
fallback, preserving the JDK 21 test/desktop toolchain. No paid service, API key,
backend, geocoding, offline-download feature, or database migration is added.

## Verification

- Compile Android and iOS simulator Kotlin before implementing the preview.
- Run existing JVM/Android host tests and preview scale/coordinate-format checks.
- Build Android app/test APKs, run debug/release lint, and build the actual iOS host.
- Update existing journeys to configure onboarding language through stored/system
  preferences, and exercise explicit switching in Settings.
- Check saved-place restoration, failed recapture, radius updates, map retry,
  English/Arabic, RTL, light/dark, large text, and screen-reader descriptions.
- Use Android Studio's formatter for changed Kotlin files and run the production
  code guard. Native rendering and real-device behavior require device evidence;
  compilation and fake-location tests do not establish those acceptance gates.

## Sources

- [MapLibre integration requirements](https://maplibre.org/maplibre-compose/getting-started/)
- [OpenFreeMap mobile integration and styles](https://openfreemap.org/quick_start/)
- [OpenFreeMap privacy](https://openfreemap.org/privacy/)

## Implementation outcome — 2026-11-04

Implemented in the existing workspace. Android app/test APKs, debug/release lint,
173 JVM tests, 95 Android host tests, and arm64 iOS app/test builds passed.
Lint reports nine warnings per variant in unchanged Android files/resources.
Android Studio formatting, resource checks, independent review, production-code
guard, and `git diff --check` passed. The review's iOS test-isolation finding was
addressed with cleanup of the test-created tracker through local-data confirmation.

Connected/native journeys, actual map rendering, offline UI transitions, native
accessibility, and screenshot evidence remain unverified. Test-bundle compilation
is not a native UI acceptance pass. Detailed local evidence is in
`artifacts/2026-11-04-onboarding-preview-verification.md` (ignored build evidence).
