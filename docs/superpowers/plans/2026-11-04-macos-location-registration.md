# macOS location registration

User request, 4 November 2026: implement real desktop place registration, macOS only.
This expands the desktop development host's foreground location support; it does not
change the frozen Android/iOS product contract or authorize desktop publication.

Use Apple's Core Location in a bundled Swift helper app, called by the desktop
composition root through the existing LocationGateway and LocationPermissionPort.
This avoids a new JNI dependency and keeps native APIs out of common code.
Browser/IP geolocation cannot satisfy native freshness evidence; manually entered
coordinates do not implement “Use my current location”. Neither is used.

- [x] Restore SetupPlace's registration action, retain localized labels, and expose
  settings recovery for denied permission and disabled location services.
- [x] Add a macOS helper with status, permission, and bounded one-shot fix commands,
  native timestamp/accuracy evidence, localized purpose strings, and no coordinate logs.
- [x] Add a cancellable desktop process bridge, validate its cross-process replies,
  and connect both permission and location interfaces in DesktopAppGraph.
- [x] Build the helper for Gradle run and bundled distributions without committing binaries.
- [x] Test malformed/invalid/failure replies, bridge delays, timeout/cancellation,
  permission changes; run desktop/shared JVM checks and compile Swift.

Review: native samples retain capture times and age; no fake coordinates or weakened
50 m accuracy / 30 s freshness thresholds; process cancellation stops acquisition;
no background permission or retries. Live macOS permission/settings/location evidence
is required separately from deterministic JVM tests. Existing unrelated edits remain.

Verification: native Swift compilation, 8 desktop tests and 173 shared JVM tests pass.
Gradle prepareAppResources includes the helper and is ordered after native compilation.
Native permission was granted by the user; capabilities report authorized, precise,
and services enabled. The settings command visibly opened Location Services.
A live bounded acquisition returned cached evidence, 77 m accuracy and about 956 s old;
this does not pass registration. Successful real registration remains an unpassed
hardware acceptance gate. The Java UI could not be inspected through the available
computer-use app binding. No coordinates were logged or retained as test evidence.

## Direct IDE launch follow-up

The desktop launcher originally assumed Compose Gradle supplied
`compose.application.resources.dir`, causing MainKt startup to fail in direct IDE
launches. Include the generated native app in the main runtime resources and make
processResources depend on native compilation. Resolve the executable from the
runtime classpath when the distribution property is absent. Regression tests cover
both launch paths, including the helper executable and its adjacent Info.plist.
