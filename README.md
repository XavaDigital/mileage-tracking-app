# Mileage Log

Android app for tracking business kilometres across multiple businesses, for two drivers,
with CSV export for the accountant. See [docs/PLAN.md](docs/PLAN.md) for the full feature plan.

## Status

**Phase 1** — manual start/stop trip recording (GPS foreground service), local Room database,
trip history with work/personal + business classification, CSV export via share sheet.

**Phase 2** — automatic trip detection from the car's Bluetooth (connect = start,
disconnect = stop), end-of-trip notification with one-tap Work…/Personal/Passenger
actions, merge-into-previous-trip for petrol stops, settings screen (car picker,
background location + battery-exemption onboarding).

Upcoming: shared Google Sheet sync (phase 3), NFC tag trigger + polish (phase 4).

## Building

Requires JDK 17+ and the Android SDK (a `local.properties` with `sdk.dir` pointing at it).

```
./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`. Install with:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Stack

- Kotlin + Jetpack Compose (Material 3), single `:app` module
- Room (SQLite) as local source of truth
- FusedLocationProvider in a foreground service for trip recording
- DataStore for settings (driver name)
- No backend — data stays on-device until the Google Sheets sync phase
