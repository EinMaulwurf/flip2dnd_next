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
