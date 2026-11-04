# Personal v3: reliable flips with adaptive sensor monitoring

Version: `12.1.1-personal.3` (`1214`). This update includes the preceding flip/vibration reliability fixes.

## Screen-off power strategy

The service selects a strategy by checking both sensor capabilities and registration results:

1. **Wake-up tilt detector:** Arm the Android-defined `android.sensor.tilt_detector` special-trigger sensor. Take a four-second accelerometer snapshot at startup and after each tilt. Then unregister the accelerometer and release the CPU wake lock. While resting, only the hardware tilt detector remains armed; there is no polling timer or gyro stream. Android's tilt detector uses an averaged gravity direction, so detection can take a few seconds rather than being instantaneous.
2. **Wake-up accelerometer:** If tilt detection is unavailable or registration fails, request accelerometer samples at 2 Hz with up to one second of batching. No persistent CPU wake lock is held. Sensor hardware continues to consume power and wakes the CPU to deliver readings; actual batching depends on the device.
3. **Legacy hardware:** If neither suitable wake-up sensor is usable, sample the ordinary accelerometer at 5 Hz and keep a renewable partial wake lock while the screen is off. This uses more power, but removing the lock would silently lose flips during CPU suspend. The ongoing notification explicitly says **Higher battery use on this device**. This mode is not an assurance of low overnight drain.

Screen-on detection uses the ordinary accelerometer without a wake lock. A continuously running gyroscope is no longer needed. The separate sensor used by the home-screen orientation display unregisters when the screen turns off or its collector is cancelled.

## Finite work after a flip

Sensor-delivery wake locks last at most two seconds. Tilt snapshots normally hold a five-second safety lease and release it after four seconds. A configured activation delay and its Total Silence transition keep sampling alive, so pickup can still cancel activation after the initial snapshot would have finished. Transition jobs have a 25-second coroutine deadline and a 30-second wake-lock safety timeout; cancellation releases their locks. Asynchronous feedback gets its own short-lived transition lease so CPU sleep cannot interrupt its settling delay.

Sample timestamps, rather than callback delivery times, drive orientation dwell checks. This preserves stability detection when several sensor samples arrive in one batch. Hysteresis and dwell checks reject brief bumps and feedback vibration. Pickup feedback belongs to the mode transition and is not cancelled by an unrelated screen-state event. Amplitude-capable motors preserve the selected rhythm; simpler motors use duration adjustment as the fallback strength control.

## Event-driven state and filters

System DND/ringer broadcasts replace the repository's one-second polling loop. Screen, proximity, audio-device, audio-playback, flashlight, settings and activation-state events trigger eligibility checks. Android 6–7 lack audio-playback callbacks; only an outstanding media blocker on those versions uses a five-second retry without its own wake lock.

DND schedules use one inexact, idle-allowed wake-up alarm at the next real schedule change, rather than repeatedly waking to check the clock. The alarm is cancelled on service shutdown and recalculated after settings, clock or timezone changes. Android may delay inexact alarms during idle, so schedule boundaries are not guaranteed to execute at an exact second.

## Validation and limits

Unit tests cover hours of simulated rest with no additional tilt-mode acquisitions or accelerometer sampling, pickup after rest, repeated tilt events, screen changes during sampling, stopping/cancellation, failed registrations, both fallback paths, long activation delays, orientation noise, feedback ordering and schedule boundaries. A simulated idle test establishes software lifecycle behavior; it does not measure battery consumption or prove a particular phone exposes usable wake-up sensors.

Build verification: all **38 core unit tests pass**, debug APK assembly succeeds, and `git diff --check` passes. Command: `./scripts/build-apk.sh :core:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Lint completes with the existing 25 translation errors (11 missing and 14 extra translations), and no API-level errors; the project configures lint not to fail the build on errors. The v3 APK passes signature verification and uses the same signing certificate as Personal v2, allowing an in-place upgrade.

No physical phone was connected during this change. Before relying on overnight behavior:

- Check the ongoing notification for battery-saving mode versus higher battery use.
- Verify repeated flips with the display off, including pickup during the longest activation delay and Total Silence feedback.
- Leave the phone motionless for several hours, then pick it up and verify restoration.
- Compare battery use over matched idle periods with the service on and off. Charging, radio conditions, always-on display and other apps should be comparable. Use Android's battery page or `adb shell dumpsys batterystats` to investigate; a short functional test cannot establish a reliable drain percentage.

References: [Android sensor types and tilt semantics](https://source.android.com/docs/core/interaction/sensors/sensor-types), [sensor registration and batching](https://developer.android.com/reference/android/hardware/SensorManager), [CPU suspend and wake-up sensors](https://source.android.com/docs/core/interaction/sensors/suspend-mode).

## Recovery after process death (November 2026)

The November 4 diagnostic report showed PID 16144 logging until November 3 at
22:25:15 Europe/Berlin, then PID 23247 starting at 06:04:20 the next morning.
There was no recorded service shutdown or exception. DND and the saved ownership
session survived; startup detected face-up and restored normal interruptions at
06:04:21.323. The report establishes process replacement, but not its exact death
time or whether a crash, memory pressure, or manufacturer policy caused it.
Battery optimization exemption was already enabled.

The detector already uses a foreground service and `START_STICKY`. A separate
15-minute periodic JobScheduler job now checks whether detection is enabled but
not running, and requests a foreground-service restart. Scheduling is idempotent;
a healthy detector is left alone. Pause cancels recovery, and a stale job checks
the saved enabled flag before doing anything. Jobs survive process death but are
not persisted across reboot: the existing boot receiver remains responsible for
respecting Auto Start. No extra dependency, exact alarm permission, network work,
or continuous wake lock is required.

This is best-effort recovery, not a 15-minute maximum outage. Android can defer
jobs during idle or apply quotas; background foreground-service starts require
an exemption (such as disabled battery optimization on Android 12+). Rejected
starts are logged and can be retried at the next periodic execution. Force-stop
and manufacturer restrictions can prevent recovery. DND is restored through the
existing ownership and orientation checks, not by unconditionally clearing it
while the phone is still face-down.

On Android 11+, diagnostic snapshots also include up to five system process-exit
records: timestamp, PID, numeric reason/status, importance and memory usage.
Descriptions and traces are excluded because they may contain private data.
This can distinguish crashes, ANRs and memory kills when Android records a reason.

Device acceptance: with recording enabled, terminate the process without
force-stopping the package, confirm a recovery request and new detector startup,
and verify face-up restores app-owned DND. Confirm Pause stays paused through a
scheduled job, and reboot with Auto Start disabled does not start detection.
Repeat overnight on the affected Xiaomi; automated tests cannot establish OEM
scheduling behavior or battery impact.

References: [JobScheduler periodic execution](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long)),
[background service start exemptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
[Android process-exit records](https://developer.android.com/reference/android/app/ApplicationExitInfo).

Validation for this change: `:app:testDebugUnitTest :app:lintDebug
:app:assembleDebug` passed. All 29 app tests passed, including four recovery tests
and a process-exit export test. Lint reported warnings/hints but no errors.
Physical-device recovery and overnight battery impact remain unverified.

## Pocket activation controls

Activation tolerance must now hold throughout the activation delay. The separate
**Deactivation angle**, under **Advanced detection settings**, defaults to 41° and
can be adjusted up to 90°. It stays at least 5° above activation tolerance; raising
activation tolerance raises the exit angle when necessary. The exit angle applies
to confirmed face-down orientation, while the stricter activation gate prevents a
pending activation from using that wider margin.

**Require stillness** is opt-in. It resets the activation countdown when motion
exceeds the selected sensitivity. **Motion sensitivity** appears in advanced
settings when enabled; higher values reject smaller movements. Stillness requires
at least one second of observation even with a zero activation delay. Motion does
not itself deactivate DND once activated. A flat, motionless pocket can still pass.

The filter reuses accelerometer readings, tracking deviation from a smoothed
acceleration vector and from gravity magnitude. No gyro or new permission is
required. Sensor timestamps, sample gaps, and freshness checks prevent stale or
batched samples from fabricating a quiet interval. Moving or excessively tilted
samples cancel the pending confirmation lease; normal snapshot monitoring then
resumes. Repeated attempts can extend sensor activity, so battery impact and the
sensitivity thresholds still need physical-device measurement.

Device checks: compare a flat table, a vibrating table, walking with the phone in
a pocket, and a seated train journey. Check 0- and 10-second activation delays,
screen-off operation, both angle sliders, and pickup after successful activation.
