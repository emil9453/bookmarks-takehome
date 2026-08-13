# Mobile — Android / Kotlin

## Stack

Kotlin, Jetpack Compose, Material 3, Retrofit + kotlinx.serialization, ViewModel + StateFlow,
Navigation Compose. Gradle wrapper. Target and compile against the installed SDK (35/36).

## Device

**There is no emulator on this machine** — no emulator binary, no system image. The app runs on
a physical device over USB. Confirm `adb devices` lists it before doing anything else.

```bash
./gradlew assembleDebug           # build
./gradlew installDebug            # build + push to the connected device
./gradlew testDebugUnitTest       # unit tests
./gradlew assembleRelease         # the APK that ships
adb devices                       # device connected?
adb logcat -s BookmarksApp        # app logs
```

## Decisions specific to this project

Each is a deliberate omission with a stated trigger for when it would be worth adding. Be ready
to defend all three — "I skipped it and here is the line where I'd add it" is a stronger answer
than unexplained ceremony.

**No dependency-injection framework.** Two things need wiring. A small hand-written holder
covers it. Hilt earns its place at roughly five injectables or the first multi-module split.

**No paging library.** A sealed `UiState` makes the four required states — loading, data, empty,
error — literal and demonstrable. The paging library buries them inside its own load model.
List paging is a manual load-more when the last item comes into view.

**No local database, no offline mode.** Not asked for, and it brings sync conflicts with it.

**All four states are a hard requirement.** The brief names them explicitly and they will be
asked for on demand — including the error one. Each must look visibly different. The empty
state tells a first-time user what to do; the "no search results" state is distinct from
"nothing saved yet".

**Search is debounced at ~300ms**, and a stale response can never overwrite a newer one.
`MutableStateFlow` → `debounce` → `distinctUntilChanged` → `flatMapLatest`. The last operator
is what cancels the in-flight request; without it a slow early query can land last and win.

**Never re-sort results on the phone.** The backend ranks them. Rendering them in a different
order throws away the most interesting thing in the project.

## Backend URL

Configured per build type, never hardcoded in a call site.

**Both build types point at the deployed HTTPS backend.** The backend is deployed before this
session starts, specifically so there is no local-networking problem to solve — no `adb
reverse`, no hunting for the host's LAN IP, no cleartext exception. HTTPS works from a USB
device with no configuration at all.

Only reach for a local backend if you are actively changing the API, and then use
`adb reverse tcp:8080 tcp:8080` plus a cleartext exception scoped to that host. Default to the
deployed URL.

The backend is self-hosted and does not sleep — every request including the first is ~250ms. It
used to be on a free tier that slept after 15 minutes idle and took ~60s to wake, which is why
the HTTP timeouts were once 30/120/150; they are 15/30/45 now.

## Traps

- Kotlin from an AI tool often compiles but leaks coroutine scope, or collects a flow outside
  the lifecycle. Watch for work that keeps running after the screen is gone.
- Compose recomposition: state read in the wrong scope silently re-runs work every frame.
- `LaunchedEffect` with an unstable key restarts on every recomposition.

First time writing Kotlin is explicitly fine per the brief, and AI assistance is expected.
When a tool hands you Kotlin that is wrong, **log it to `NOTES-scratch.md` immediately** — one
of those entries is a required deliverable.

## Done means

`./gradlew testDebugUnitTest` passes, the app is installed and exercised on the real device,
and every "Done when" bullet on the Linear issue is satisfied.
