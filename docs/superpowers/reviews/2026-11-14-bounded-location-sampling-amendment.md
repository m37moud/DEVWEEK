# Bounded location sampling amendment

Approved by the user on 14 November 2026 through the bounded location sampling implementation plan.

An Android automatic attempt may collect multiple native sensor updates in one acquisition session after its atomic claim. Registration and manual checks on Android/iOS use the same bounded sampling policy. This replaces the implementation plan’s one-sample restriction, not the one-attempt restriction. The frozen v1 reference remains unchanged.

The session lasts at most 30 seconds; automatic acquisition is additionally limited by the remaining arrival-to-deadline window. Return the first structurally valid fix with reported accuracy at most 50 m and established age at most 30 seconds. Distance and capture-time attendance classification remain authoritative. Never wait for an outside-area user to move inside. Cancellation, failure, and process death consume the automatic claim; no restart, periodic retry, persistent service, or background iOS location is introduced.

At timeout, pass the latest structurally valid rejected sample to existing validation for inaccurate/stale feedback, or return timeout if no sample arrived. Retain coordinates only during acquisition and verdict calculation. Native errors and cancellation preserve their existing failure semantics.

Physical-device accuracy and background delivery remain best-effort and require separate evidence; source tests and simulator results do not establish those gates.
