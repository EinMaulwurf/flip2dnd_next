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
