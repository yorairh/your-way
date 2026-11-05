# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is
"Your Way": an Android app (Kotlin, Jetpack Compose, Material 3, MapLibre) that plans Israeli public transport trips (bus, train, light rail, short walks). Routing runs on the phone from a bundled schedule; there is no server. UI is English and Hebrew (RTL). The Gradle project is in `android/`; `schedule/` holds the Python schedule builders. See `README.md` for status and credits.

## Commands
Run from `android/` (JDK 17+ and Android SDK needed). The project path contains Hebrew characters, so quote paths, and `android.overridePathCheck=true` is already set. Build output goes to `~/.yourway-build` (outside the project, because Gradle's test runner fails on the Hebrew folder name), not `android/build`.

    ./gradlew assembleDebug testDebugUnitTest      # build + all tests
    ./gradlew testDebugUnitTest --tests "com.yourway.app.TripPlannerTest"   # one test class
    # APK: ~/.yourway-build/app/outputs/apk/debug/app-debug.apk

On this machine set `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` first. There is no linter configured.

Rebuild the bundled schedule (both files from the same GTFS zip from gtfs.mot.gov.il, because the schedule refers to stops by position in `stops.dat`):

    python schedule/build_stops.py gtfs.zip android/app/src/main/assets/stops.dat
    python schedule/build_schedule.py gtfs.zip android/app/src/main/assets/schedule.dat

Do not run these with `python -I` (breaks the script's imports). The assets are gzipped `.dat` files, and the bundled schedule only covers dates up to 2 December 2026 (newer ones arrive through the nightly job below).

## Architecture
- **Data flow:** `AppContainer` wires everything. `TripRepository` loads `Schedule` (compact arrays read from `schedule.dat`) and `StopDirectory` (from `stops.dat`), asks `TripPlanner` for itineraries, then optionally passes them through `LiveUpdater`. `AppViewModel` is the single state holder for all screens (`Screen` enum; no nav library).
- **Routing (`routing/`):** `Raptor.kt` is the round-based search over `Schedule` patterns. `TripPlanner.kt` runs it, snaps the start/destination to nearby boarding stops, converts results to `Itinerary` (legs + `RideRef`), ranks them (cost = arrival + 0.5 x walk seconds + 300 s per transfer) and keeps the top 3. It then groups routes that board and alight at the same stops (`journeyKey`) into one `Itinerary` with `alternatives` (other lines/times, found by re-searching with each found line excluded; 40-minute window, max 5). Single-ride routes only gain alternatives.
- **Walking:** straight-line estimates in `data/Geo.kt`; anything under `AT_STOP_METERS` (20 m) counts as 0 (already at the stop).
- **Live delays:** `routing/LiveUpdater` + `data/StrideLiveSource` query Open Bus Stride (community service, may be down) for the bus's last position, project it onto the stop-to-stop segments to estimate delay, and shift later legs; failures or a 7 s timeout leave scheduled times. `line_ref` equals the GTFS `route_id`, which is why `Schedule` carries `routeId`/`routeOperator`. Trains and light rail are timetable only. A delayed ride that breaks a connection sets `missedTransfer` and sorts the route last.
- **Places:** `PhotonSearch` (Photon, OSM) for addresses and known places; stops come from `StopDirectory`. Hebrew names are shown only when the app language is Hebrew.
- **UI (`ui/`):** `AppScaffold` in `CommonUi.kt` draws the themed random rails/trains background and top bar; `liveColor`/`liveStatusText` define live-status colours (grey timetable, green <=1 min, orange 2-9, red >=10). `RouteMap.kt` draws the route with MapLibre. `CrashReporter` saves crashes and `CrashScreen` offers copy/share on next launch.
- **Schedule file format:** `build_schedule.py` writes the arrays and `routing/Schedule.read()` reads them in the same order; new arrays go at the end and `read()` needs an end-of-file fallback so older files still load. Changing the format means rebuilding `schedule.dat` and updating `tests/TestSchedule.kt`.

## Testing notes
- Tests (about 75) live in `app/src/test`; UI tests use Robolectric + Compose (`PickerTestBase`, `LanguageTestBase`), and `RealScheduleTest` runs against the real bundled schedule. `forkEvery = 1` is set.
- Tests that use `org.json` (e.g. Photon/Stride parsing) must use the Robolectric runner; plain unit tests don't have it.
- UI tests that tap top-bar icons break if the TopAppBar is clipped.
- Strings are in `res/values/strings.xml` and `res/values-iw/strings.xml`; add both. In Hebrew use the geresh character instead of apostrophes.
- Nothing has been run on a real phone yet; describe builds as tested on a PC only.

## Releasing
`./release.sh "notes"` runs the tests, builds the signed release APK (`assembleRelease`) and creates the GitHub release `v<versionName>` with `YourWay-<version>.apk`. Signing config comes from `~/.yourway-release/signing.properties` (key stays out of git). Bump `versionCode`/`versionName` first. Releases before 0.10.0 were debug-signed, so phones must uninstall once to move to the release key.

## Automatic schedule updates
`.github/workflows/schedule.yml` (nightly, 22:30 UTC, or run by hand) builds `schedule.dat`/`stops.dat` with the same scripts, publishes release `schedule-<date>`, then updates `schedule-latest/manifest.json` (written by `schedule/make_manifest.py`). In the app, `data/ScheduleUpdater.kt` fetches the manifest on a background thread from `YourWayApp`, downloads to a staging folder, verifies size+sha256, loads both files to prove they work and match, then swaps; `AppContainer.create` uses the downloaded pair (`filesDir/schedule/<version>`) if valid, else the bundled assets, and a new app version discards the download. The GTFS download must use GET (HEAD returns a web page). Do not run the build scripts with `python -I`. The updater is skipped under Robolectric.
