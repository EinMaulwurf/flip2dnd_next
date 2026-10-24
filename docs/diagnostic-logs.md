# Recording a one- or two-day test

1. Open **Settings → App settings → Diagnostics** and enable **Record diagnostic log**.
2. Use the phone normally for one or two days. Keep detection enabled as usual.
   If a flip is missed or sound is not restored, note the approximate local time
   and what you expected to happen. Include whether the screen was off and whether
   media, headphones, proximity gating or a schedule might have affected the flip.
3. Tap **Export log**, then save or share the text file through Android's share
   sheet. Send that file with your notes when reporting back. Recording can remain
   enabled during export.
4. Disable recording after the test. **Clear log** removes recorded events and
   previous export files from this phone.

Recording is off by default. Enabling it persists across app/service restarts and
reboots. Disabling recording preserves the existing log so it can still be exported.
There is no automatic upload and no additional storage or logcat permission.

The recorder keeps up to **2 MiB** in four rotating files. New events replace the
oldest data once that limit is reached. It also keeps at most three exported
reports in the app's private cache; these can each contain the full recorded log.
Recording data and its enabled state are excluded from backup/device transfer.

Exports contain timestamped JSON records, one per line. The header includes app
version, Android version, manufacturer/model, sensor capabilities, current
settings overrides, current session ownership, battery/power state and permissions.
Events cover service lifecycle, sensor mode/registration, confirmed orientation
and delivery delay, activation decisions/blockers, DND restoration/manual overrides,
and changes to screen, power, clock and relevant settings. Elapsed-realtime timestamps
and process IDs help distinguish clock changes and restarts. Log timestamps are UTC;
the device timezone is also included for interpreting local schedules.

Notification contents, custom sound URIs, media contents, other apps' logs, serial
numbers and account identifiers are not collected. Exception records include class
names and code locations, not exception messages that could include private paths.
Only the report folder is exposed through a read-only content URI when you export.

Logging uses a bounded asynchronous queue and records events rather than raw
accelerometer samples. It adds no polling timer, sensor subscription, wake-up alarm
or wake lock. File writes still have some overhead. A full queue records a dropped-event
count, storage failures are shown in Settings, and an interrupted final file record
is discarded on reopening. Android can kill the process without a shutdown callback;
a restart/gap is evidence to investigate, not proof of a particular cause. The log
is useful for functional diagnosis, but does not replace a matched service-on/off
battery comparison or prove overnight delivery on a particular phone.
