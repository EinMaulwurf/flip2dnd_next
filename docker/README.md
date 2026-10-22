Build an installable debug APK with Java 17 and Android SDK 36 in Docker:

```sh
./scripts/build-apk.sh
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
Build caches and the debug signing key persist in `.build-cache/`; keep that directory
if you want subsequent APKs to update an existing installation. The debug APK has a
`.personal` application ID suffix, so it can coexist with the upstream app. Disable
the upstream detector before enabling this version.

No Android Studio, emulator, NDK, or phone-specific build is required. Budget roughly
3–6 GB after the first build, with extra space for Docker's build cache. Run other
Gradle tasks by passing arguments, e.g. `./scripts/build-apk.sh :app:lintDebug`.

Personal build changes:

- Settings opens as its own screen, with separate behavior, feedback, filters, app,
  and extras categories. Onboarding pages scroll independently of the Next button.
- Vibration uses a continuous 0–100% slider. Zero is silent; low values reduce both
  amplitude and pulse length. Actual minimum strength depends on the phone's motor
  and Android amplitude-control support. Previews use the same mapping as DND.
- Enable **Auto Start** in the App Settings category for boot/update startup. Open
  the app once and grant DND access first. Android force-stop prevents automatic
  startup until the app is opened again. On Xiaomi, also enable Background autostart
  in system settings (search for it if the location differs on your HyperOS version).
- Proximity detection is optional and off initially. When enabled, the proximity
  sensor must report covered as well as the phone being face down before DND starts.
  An unavailable sensor disables the option. Test this on your phone before relying
  on it: sensor behavior can vary when the screen is off. Other Pro features remain
  as provided by the public repository; this does not include the private Pro module.

Phone acceptance checks (requires an actual device):

1. During onboarding, scroll the permissions page and open Accessibility settings.
   Repeat with larger system text. Next must remain accessible.
2. Open Settings, switch categories, change a setting, then use Android Back to
   return home. Rotate the phone and confirm the selected category is retained.
3. Enable custom vibration, compare 0%, 5%, 10%, 50%, and 100%, then flip the phone.
   Check both preview and actual DND feedback; 0% must produce no vibration.
4. Enable Auto Start, reboot, unlock, and check the detector notification and a flip.
   Install a newer APK with the same package and signing key and repeat.
5. Enable proximity detection, hold the phone face down with the sensor uncovered,
   then cover it without moving the phone. DND should activate after the delay;
   uncovering during the delay should cancel activation. Turning face up restores
   the prior DND state as usual. Repeat with the screen off.

Validation on 2026-10-22:

- `:app:assembleDebug`: successful, Java 17 / SDK 36 in Docker.
- `:core:testDebugUnitTest`: 4 tests passed, 0 failures or skips.
- `apksigner verify`: successful (v1 and v2 signatures).
- APK metadata: `dev.robin.flip_2_dnd.personal`, version `12.1.1-personal.1`
  (1212), launcher label `Flip 2 DND Personal`, including German. Contains arm64-v8a.
- `:app:lintDebug`: completed with 50 errors, 212 warnings, and 7 hints. The build
  inherits `abortOnError = false`; this is not a clean lint result. Errors comprise
  25 older-Android API checks, 14 extra translations, and 11 missing translations
  (including English fallback for the three new messages outside German). No
  remaining indentation findings. The API findings are in upstream code that
  supports Android 6+; the new feature code is not flagged for API compatibility.
- No physical device or emulator testing was performed. Boot/update behavior,
  proximity sensor behavior, and perceived vibration strength require phone checks.

The generated APK is copied to `artifacts/Flip-2-DND-Personal.apk`, with its SHA-256
in the adjacent `.sha256` file. Build artifacts and signing keys are not committed.
