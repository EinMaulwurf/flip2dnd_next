# Reliability audit — 2026-10-24

Scope: flip detection, DND/ringer ownership, startup/shutdown, settings changes,
hardware failures, and verification. Reviewed the working tree while the separate
rename was in progress; naming and package migration are outside this audit.
Findings below are source-level findings, not claims of physical-device reproduction.
No application code was changed for this audit.

## Findings

### 1. High: switching activation mode strands the previous mode

`SettingsRepositoryImpl.kt:635` publishes the new activation mode and restarts the
service without ending the existing session. `DndRepositoryImpl.kt:80` chooses
the restoration operation using the **current setting**, rather than the mode
that created the session. The persisted session contains only an `owned` boolean.

Example: activate silent ringer mode, then select DND while the session is active.
On a device where silent ringer mode does not itself enable DND, the repository
resynchronizes to DND-off and clears ownership; the ringer remains silent with no
session left to restore it. Conversely, switching from active DND to ringer can
run ringer restoration while leaving DND on.

Fix: persist the activation mechanism and previous/applied state in the session.
Finish the old session before switching modes, or defer the setting to the next
session. Restore using the session, never the current preference.

### 2. High: explicit stop leaves app-owned silence active

`MainViewModel.kt:61` and `FlipDndWidgetProvider.kt:43` stop the service directly.
`FlipDetectorService.kt:449` cancels work and unregisters sensors but never restores
app-owned DND/ringer state. `DndService.cleanup()` only handles feedback resources.

Example: flip to silence, stop detection using the tile/widget, then pick up the
phone. No sensor listener remains to restore sound. Silence persists until a
manual system change or a later service start.

Fix: introduce an explicit stop command that restores an owned session before
stopping. Keep crash/process-restart recovery separate; do not depend on
`onDestroy()` for process-death recovery.

### 3. High: manual ringer changes are overwritten on pickup

`DndRepositoryImpl.kt:152` deliberately does nothing to reconcile activation in
ringer mode, even when receiving `RINGER_MODE_CHANGED_ACTION`. DND broadcasts
should not control ringer state, but actual ringer changes must be reconciled.

Example: begin in normal mode, let the app switch to silent, manually choose
vibrate, then pick up the phone. The repository still reports the session as
active and restores normal, overriding the user's newer selection.

Fix: compare the actual ringer state against the state applied by the session;
relinquish ownership when it differs. Test both manual changes and broadcasts
caused by the app itself.

### 4. High on Android 6–7: activity startup calls an unavailable API

`MainActivity.kt:312` calls `startForegroundService()` without a version check,
although the declared minimum SDK is 23. This method was added in API 26.
Completing permission setup can therefore crash on API 23–25.

Fix: use `ContextCompat.startForegroundService()` as the boot receiver already
does, or explicitly branch by API level. Verify API 23/25 startup, not just a
modern debug build. The lint baseline includes this historical API violation.

Reference: [Android Context API](https://developer.android.google.cn/reference/android/content/Context#startForegroundService(android.content.Intent)).

### 5. Medium: pause is not a durable user choice

`SettingsRepositoryImpl.kt:217` unconditionally stops and starts detection for
most settings changes, including sound and vibration. It never checks whether
the user had paused detection. `MainActivity.kt:270` also starts detection whenever
its permission check passes. There is no persisted desired-running state.

Example: pause detection, change vibration strength, then put the phone down.
Detection has silently restarted. Frequent settings edits also create avoidable
sensor gaps and cancel pending transitions.

Fix: persist desired enabled/paused state and use it consistently in the activity,
widget, tile, boot receiver, and recovery path. Apply settings through the existing
flows; avoid restarting a service for values it can observe/read directly.

### 6. Medium: unavailable sensors look like working detection

`SensorMonitoringSession.kt:49` and `:62` can finish with mode `STOPPED` after all
registration attempts fail. The foreground service stays running and
`FlipDetectorService.kt:569` displays its generic monitoring notification. The UI
checks service existence, not successful monitoring. Accelerometer hardware is
optional in the manifest.

Separately, `FreeDetectionManager.kt:33` ignores proximity registration failure
and reports uncovered when no sensor exists. Enabling proximity gating on such
a device blocks every activation without explaining why.

Fix: distinguish intentional suspension from unavailable/failed hardware; expose
an actionable error state. Check proximity support and registration results before
allowing it to become a mandatory gate.

### 7. Medium: transient session ownership is included in backup

`backup_rules.xml` and `data_extraction_rules.xml` contain no exclusions. The
`flip_dnd_session` preferences therefore travel with persistent user settings,
even though an ownership claim belongs to one device/session. On restoration,
`DndService.kt:75` trusts the saved boolean when the destination's DND is active.

Example: restore an owned session onto another device with DND already enabled;
pickup can be treated as permission to turn off unrelated DND (especially on
pre-Android-15 global DND APIs).

Fix: exclude transient ownership from cloud backup and device transfer and
validate recovered sessions against actual device state before restoration.

## Android 15+ design concern requiring device verification

The app targets SDK 35. Android documents that `setInterruptionFilter()` now
changes an app-associated automatic rule, while `currentInterruptionFilter`
still represents the global result. The repository and service use that global
result as their activation state. Test overlapping Bedtime/other DND rules,
manual rule overrides, and turning the app's rule off while another rule remains
active. App ownership and effective system silence need separate representations.

Reference: [NotificationManager API](https://developer.android.com/reference/android/app/NotificationManager#setInterruptionFilter(int)).

## Verification and recommended sequence

The existing core tests exercise sensor strategy, orientation stability,
activation delay, feedback order, vibration, and schedule boundaries. Those are
useful foundations. They do not exercise the Android service/repository integration
where the findings above live; there are no app-module tests in the reviewed tree.

Prioritize session ownership/restoration and explicit lifecycle commands, then
hardware/error reporting. Add integration coverage for mode changes, explicit
stop, manual overrides, process recreation, and paused settings edits. Make lint
errors enforceable after triaging the inherited baseline and translations.

Before calling this dependable, run real-device screen-off pickup tests after
hours of rest, reboot/update recovery, permission revocation, longest activation
delay cancellation, and competing Android 15+ DND rules. Measure overnight power
on both wake-up-sensor and legacy hardware. Simulated time cannot establish actual
sensor delivery or battery drain.

Executed `./scripts/build-apk.sh :core:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
All 38 core tests passed with zero failures/errors. App assembly failed at
`:app:hiltJavaCompileDebug` because generated Hilt inputs still referenced
`dev.robin.flip_2_dnd.FlipDndApp` during the package rename. Lint was not completed
by this run. The first build attempt was terminated (exit 143); these results
come from the second attempt. See `.build-cache/audit-build.log` for its output.
No device testing was performed. No rename-related source changes were made.
