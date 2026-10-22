Build the Personal APK with Java 17 and Android SDK 36 in Docker:

```sh
./scripts/build-apk.sh
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
Build caches and the debug signing key persist in `.build-cache/`; keep that directory
for subsequent APKs to update an existing installation. The `.personal` application ID
allows the app to coexist with upstream. Disable upstream detection before using it.
No Android Studio, emulator, NDK, or phone-specific build is required. Budget roughly
3–6 GB after the first build, plus temporary Docker build cache.

```sh
./scripts/build-apk.sh :core:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Implemented Personal features:

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
- Adjustable activation delay (0–10 seconds) and working sensitivity control.
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
This fork implements its own features and does not include the private Pro module.

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

The generated APK is copied to `artifacts/Flip-2-DND-Personal.apk`, with its SHA-256
in the adjacent `.sha256` file. Build artifacts and signing keys are not committed.
Physical-device testing is still required for boot/update, proximity and haptics.
