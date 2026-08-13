# BirBookmarks — Android app

Native Android client for the Bookmarks API. Kotlin, Jetpack Compose, Material 3.

It wears the Birbank design language — their red, their typeface (Onest), their mark — because the
API and the client are separable from presentation, and the cheapest way to show that is to change
the skin without touching a request, a model or a ViewModel. The reasoning is in
[NOTES.md](../NOTES.md).

## The APK

The signed APK is attached to the GitHub release. Download it to the phone and open it; Android
will ask to allow installing from this source.

To build it yourself:

```bash
cd mobile
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

A fresh clone has no signing key (it is deliberately not in the repository), so that command
produces an **unsigned** APK, which Android refuses to install. Either use the APK from the
release, or build the debug one, which is signed with the local debug key:

```bash
./gradlew installDebug        # builds and installs onto a connected device
```

## Which backend it talks to

Both build types point at the deployed API:

```
https://bookmarks.178.104.76.109.sslip.io
```

It is set in one place — `backendUrl` in [app/build.gradle.kts](app/build.gradle.kts) — and reaches
the code as `BuildConfig.BASE_URL`. No call site contains a URL. There is no localhost anywhere:
that is why a USB-connected phone needs no `adb reverse`, no LAN address and no cleartext
exception.

The host is a small VPS running the API in Docker under Coolify, behind Traefik with a Let's
Encrypt certificate. It does not sleep, so every request including the first is around 250ms.

It used to sit on a free tier that slept after 15 minutes idle and took a measured 62.6s to wake —
once over 120s. The HTTP timeouts were sized for that (connect 30s, read 120s, call 150s) and have
come back down to 15/30/45 now that the wait is gone: still far above OkHttp's 10s read default,
because the slow case that remains is a bad mobile network, not a waking container.

## Requirements to build

- **JDK 21** (the build uses whatever `JAVA_HOME` points at)
- **Android SDK with platform 36** installed
- No Android Studio needed — everything below runs from the command line
- A physical device over USB, or an emulator if you have one

Dependency versions are pinned below the newest releases on purpose. The 2026 AndroidX trains
publish `minCompileSdk=37` and `minAgpVersion=9.1` in their AAR metadata and refuse to build against
platform 36. Lint's "newer version available" warnings are the expected consequence of that pin, not
an oversight.

## Commands

```bash
./gradlew assembleDebug           # build the debug APK
./gradlew installDebug            # build and install on the connected device
./gradlew testDebugUnitTest       # unit tests
./gradlew lintDebug               # Android lint
./gradlew assembleRelease         # the APK that ships (needs the signing key)
adb devices                       # is the phone connected?
```

## How it is put together

```
data/     Retrofit interface, wire models, the RFC 9457 error body, and Network — the hand-written
          object that constructs the Json, the OkHttpClient and the Retrofit
ui/       One ViewModel and one Composable screen per destination: list, add, detail
ui/theme/ The Birbank palette, the Onest typography and the shapes — the single source of every
          colour on screen, so no Material default is ever constructed
MainActivity.kt   the Compose entry point and the navigation graph
```

Three deliberate omissions, each with the trigger for when it would be worth adding:

- **No dependency-injection framework.** Three objects need constructing. `Network` is the seam;
  Hilt earns its place at roughly five injectables or the first multi-module split.
- **No paging library.** The four states the brief asks for are four types in a sealed interface,
  which makes them literal and demonstrable. Paging 3 owns the loading model and buries them inside
  `LoadState`. Worth revisiting if the list ever needs placeholders or cache-backed paging.
- **No local database and no offline mode.** Not asked for, and it brings sync conflicts with it.

Search is debounced at 300ms and runs through `flatMapLatest`, so a slow response for an old query
is cancelled rather than allowed to land last and overwrite a newer one. Results are rendered in
the order the backend ranked them and are never re-sorted on the phone — the ranking is the
interesting part.
