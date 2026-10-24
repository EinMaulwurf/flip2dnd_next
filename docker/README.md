Build the Flip2DND Next APK with Java 17 and Android SDK 36 in Docker:

```sh
./scripts/build-apk.sh
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
Build caches and the debug signing key persist in `.build-cache/`; keep that directory
for subsequent Flip2DND Next APKs to update an existing Next installation. The
`dev.flip2dnd.next` application ID allows it to coexist with upstream and previous
Personal builds. Settings and permissions from those builds do not transfer. Disable
their detection services before using Next.
If switching an existing checkout from the old package, run `./scripts/build-apk.sh clean`
once before building to remove generated classes from the previous namespace.

No Android Studio, emulator, NDK, or phone-specific build is required. Budget roughly
3–6 GB after the first build, plus temporary Docker build cache.

```sh
./scripts/build-apk.sh :core:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Implemented features:

- Compact settings categories and scrollable onboarding, with fixed navigation.
- Continuous vibration strength: 0% is silent; low values reduce amplitude and pulse
  length. Hardware determines the quietest usable setting. Choose DND on/off below
  the slider to preview the corresponding saved pattern; selecting a pattern also
  selects it for strength previews. Exit feedback runs after the DND/ringer change
  and a 250 ms settling delay so the transition does not truncate the waveform.
- Auto Start is on by default. A one-time migration enables it on upgrade from the
  first Personal APK, as requested; later manual changes are respected. Grant DND
  permission first. Force-stop prevents startup until the app is opened again.
  On Xiaomi also allow Background autostart in system settings.
- Optional proximity gating: DND requires both face-down orientation and a covered
  sensor. The option is off initially and disabled when no sensor is reported.
- Adjustable activation delay (0–10 seconds) and face-down tolerance (5–30° in
  1° steps). Tolerance is the allowed tilt from perfectly flat, screen down;
  smaller angles require a flatter phone. The default is 26°. Existing sensitivity
  settings convert to the nearest supported angle on upgrade.
- DND, sound and vibration schedule windows, with Sunday=1 weekday selection.
  Overnight windows belong to the day they start (Monday 22:00 includes Tuesday
  02:00). Equal start/end times mean the whole selected day. These restrict flip
  activation/feedback; they do not independently activate DND at a scheduled time.
- Media playback, wired/USB/Bluetooth audio, and flashlight detection filters.
  Filters are checked again after the activation delay. Audio filters reflect the
  system-reported playback/output devices. A new flip reevaluates changed conditions.
- Custom audio selected through Android's document picker, with persistent read
  access and asynchronous playback. Moving/deleting the file may break playback;
  select the file again if necessary.
- Phone-and-moon vector launcher artwork, adaptive/themed icons, and a transparent
  monochrome status-bar icon. Editable artwork: `assets/branding/flip-mark.svg`.
- Extras contains only the original GitHub repository and version information.

Unsupported flashlight feedback and battery-saver sync are hidden. Paid support,
updater, credits, sponsor links, and Pro badges are absent from the settings UI.
This continuation implements its own features and does not include the private Pro module.

Phone acceptance checks:

1. Scroll onboarding permissions with large system text. Accessibility and Next
   must remain accessible. Check settings categories, rotation and Android Back.
2. Set different DND on/off patterns (e.g. double/triple), use both preview selectors,
   and compare 0%, 5%, 10%, 50%, 100%. Flip down and up; actual exit feedback should
   follow the saved pattern and strength after DND turns off. Check with notifications
   disabled too. Xiaomi's own system mode-change haptics may be separate from ours.
3. Reboot and check detector startup. Install the next APK with the same key/package
   to check update startup. Verify Xiaomi Background autostart and battery settings.
4. Hold face down with proximity uncovered, then cover it without moving the phone.
   Uncovering during the delay should cancel activation. Repeat with screen off.
5. Test schedule boundaries (including overnight and unchecked days), media playback,
   headset connections and flashlight state before and during the activation delay.
6. Pick different on/off audio files, restart the app, and confirm playback. Cancel
   the picker and confirm the previous selection remains.
7. Check launcher icons with standard and themed icons, widget/Quick Settings symbols,
   and the small notification icon against both light and dark status bars.

The validated Flip2DND Next APK is copied to `artifacts/Flip2DND-Next.apk`, with its SHA-256
in the adjacent `.sha256` file. Build artifacts and signing keys are not committed.
Physical-device testing is still required for boot/update, proximity and haptics.

## Historical Personal builds

The names and artifact paths below refer to earlier builds, before the Flip2DND Next rename.

Validation for `12.1.1-personal.2` (1213), 2026-10-22:

- Docker `:core:testDebugUnitTest :app:lintDebug :app:assembleDebug` completed.
- 13 unit tests passed: 4 vibration scaling, 6 schedule-window, 3 transition-feedback
  timing/cancellation tests. No failures, errors or skipped tests.
- APK signature verification passed. The certificate matches Personal version 1,
  so this APK can update that installation while preserving settings.
- Verified package ID, version, English/German launcher labels and arm64-v8a support.
- Lint is not clean: 50 errors (25 older-Android API checks, 14 extra translations,
  11 missing translations), 229 warnings and 6 hints. The inherited build config
  does not abort on lint errors. These error categories match the first Personal
  build; the new controllers and feedback coordinator have no lint errors.
- Launcher artwork was rendered and inspected. Notification resources were checked
  for transparent backgrounds; their appearance and Xiaomi system haptics still
  need the physical-device acceptance checks above.


## Personal v3 battery and reliability update

Validation for `12.1.1-personal.3` (1214), 2026-10-22:

- All 38 core unit tests pass, including simulated multi-hour idle and wake-lock cancellation tests.
- `:app:assembleDebug` succeeds. `:app:lintDebug` completes with 25 existing translation errors and no API-level errors.
- APK signature verification passes and its certificate matches Personal v2 for in-place upgrades.
- Installable artifact: `artifacts/Flip-2-DND-Personal-v3.apk`, with an adjacent `.sha256` file.
- Hardware battery use has not been measured. See [the monitoring strategies and device checklist](../docs/reliability-and-battery.md), especially the higher-power fallback for phones without usable wake-up sensors.


## Flip2DND Next validation

Validation for `12.1.1-next.1` (1215), 2026-10-24:

- Clean Docker build passed: `clean :core:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`.
- All 38 core tests passed, with no failures or skipped tests.
- Debug APK signature verification passed. Debug and release APKs report package
  `dev.flip2dnd.next`; every packaged launcher locale reports `Flip2DND Next`.
- All eight reflection-loaded feature implementations remain in the optimized release APK.
- Lint still reports 11 existing missing-translation issues; the inherited configuration
  does not abort on lint errors. No API-level errors remain after the clean build.
- Installable local test build: `artifacts/Flip2DND-Next.apk`, with an adjacent SHA-256 file.
- The new package installs separately; previous Personal settings and permissions do not migrate.
- Physical-device behavior still needs the acceptance checks above.
