# Your Way

An Android app that plans trips on Israeli public transport: buses, trains and light rail, with short walks.
Routes are calculated on the phone, in English or Hebrew, and drawn on a map.

**Status: early test build.** It has been tested on a PC only. Live bus delays and automatic schedule updates are not built yet,
so every route uses scheduled times, and the bundled schedule stops covering dates after 2 December 2026.

## What it does
- Search from or to any stop, stop number, address or your location. Leave now, leave at, or arrive by.
- The top 3 routes by earliest arrival, with fewer transfers preferred when arrival times are close.
- Route steps and a map. Buses and light rail follow the official road shapes; trains are drawn straight between their stops,
  because the Ministry's data has no train shapes. Walking is estimated from straight-line distance.
- Saved places (home, work, favourites, recent) and a longest-walk setting, stored on the phone.

## Build
Needs JDK 17 or newer and the Android SDK.

    cd android
    ./gradlew assembleDebug testDebugUnitTest

The APK is written under `~/.yourway-build/app/outputs/apk/debug/`.

## Updating the schedule
`schedule/build_schedule.py` and `schedule/build_stops.py` turn the Ministry of Transport GTFS zip into
`android/app/src/main/assets/schedule.dat` and `stops.dat`. Build both from the same zip, because the schedule refers to stops by position.

    python schedule/build_stops.py gtfs.zip android/app/src/main/assets/stops.dat
    python schedule/build_schedule.py gtfs.zip android/app/src/main/assets/schedule.dat

## Data and credits
- Schedule and stops: Israel Ministry of Transport GTFS feed (https://gtfs.mot.gov.il). The licence terms have not been checked yet.
- Map data: (c) OpenStreetMap contributors, tiles from OpenFreeMap, drawn with MapLibre.
- Address and place search: Photon (https://photon.komoot.io), based on OpenStreetMap.
