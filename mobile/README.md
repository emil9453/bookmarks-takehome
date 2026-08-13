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
https://bookmarks-api-4i5h.onrender.com
```

It is set in one place — `backendUrl` in [app/build.gradle.kts](app/build.gradle.kts) — and reaches
the code as `BuildConfig.BASE_URL`. No call site contains a URL. There is no localhost anywhere:
that is why a USB-connected phone needs no `adb reverse`, no LAN address and no cleartext
exception.

### The first request of a session takes about a minute

The API is on a free hosting tier that **sleeps after roughly 15 minutes of inactivity**. The first
request afterwards has to wait for the container to start. Measured from a cold start: DNS 0.08s,
TCP connect 0.09s, TLS 0.10s, **total 62.6s** — the connection is immediate and the entire wait is
the server waking up.

So the first launch after a quiet period shows the loading spinner for up to a minute. That is the
host, not the app. The HTTP timeouts are sized for it deliberately (connect 30s, read 120s, call
150s); OkHttp's default 10-second read timeout fails that request every time and makes the app look
broken. Once the server is warm, responses come back in about 0.3s.

**That 62.6s is not a ceiling.** A later cold start was measured at over 120 seconds: a 120s probe
returned nothing and a retry 15s afterwards returned instantly. Wake time depends on how long the
container has been down and on the host's load that day, so it varies more than a single
measurement suggests. The 120s read timeout has less headroom than the number above implies — if a
review session opens the app on a very cold backend and sees the error state, that is why, and
pulling to refresh will succeed because the first request did the waking.

If you would rather not wait, hit the API once in a browser first and it will be warm by the time
the app opens.

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
