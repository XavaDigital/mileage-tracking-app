# Mileage Tracking App — Plan

Business mileage logger for two drivers (David + wife), multiple businesses, minimal-input
capture, exportable log for the accountant.

## Core design

**Trigger → track → prompt → sync.** Native Android (Kotlin + Compose), no server.
Local Room database is the source of truth on each phone; classified trips sync to a
shared Google Sheet both drivers (and the accountant) can see.

### Trip detection tiers

1. **Car Bluetooth (primary, automatic)** — listen for `ACL_CONNECTED` / `ACL_DISCONNECTED`
   broadcasts from the car's paired BT device. Connect = start recording, disconnect = end.
   Note: NFC tags cannot do this — Android only reads them on deliberate tap with screen on.
2. **NFC tag (fallback)** — tag on the dash toggles trip start/stop on tap.
3. **Manual** — start/stop button in app (phase 1) + widget (later).

Petrol-stop handling: if a new trip starts within ~10 min near where the last ended,
offer to merge.

**Both drivers in the car:** both phones connect to the car's BT and both record the
same trip — the driver can't be inferred from the connection. Handled in layers:

1. End-of-trip prompt includes **[Passenger]** alongside [Work…]/[Personal] — one tap
   discards the duplicate (phase 2).
2. Once the shared log exists (phase 3): overlap detection — if the other driver has a
   trip overlapping in time with ~same distance, default the prompt to "Were you a
   passenger?"; if the other copy is already classified, auto-suppress this one.
3. Optional per-car "primary driver" setting: the secondary phone asks *before*
   recording instead of after. Likely unnecessary given 1–2.

Non-goal: guessing the driver from motion/position sensors — unreliable, not worth it
for two users.

### Recording

- Foreground service, FusedLocationProvider, high accuracy, ~5 s / 10 m updates.
- Filter fixes with accuracy > 50 m and jumps implying > 200 km/h.
- Distance = summed path (raw GPS is within 1–2% of odometer — no Roads API needed).
- Reverse-geocode start/end with the built-in Geocoder; store the polyline as evidence.
- Trips under 50 m or with < 2 fixes are discarded.

### Classification (minimal input)

On trip end, a notification prompts: **[Personal] [Work…]**. Personal = one tap.
Work opens a sheet: business chips + optional purpose (autocompleted from history).
Unclassified trips queue in the app with a badge; bulk-classify from history.

### Data model (`trips` table = CSV/Sheet columns)

date, start/end time, start/end location (geocoded), distance km, driver, type
(work/personal/unclassified), business, purpose, source (manual/bluetooth/nfc),
lat/lng endpoints, polyline. Optional odometer start/end columns later (ATO-style logbooks).

### Multi-user + export

- Each phone runs the app with its own driver name (later: Google account).
- Phase 3: append classified trips to one shared Google Sheet via Sheets API;
  driver column filled from whoever's phone logged it. Accountant gets view access.
- Until then: CSV export via share sheet (email/Drive/WhatsApp).

### Android gotchas (budgeted)

- `ACCESS_BACKGROUND_LOCATION` ("Allow all the time") needed for BT-triggered starts — phase 2.
- `BLUETOOTH_CONNECT` (API 31+), `POST_NOTIFICATIONS` (API 33+) runtime permissions.
- OEM battery killers: request battery-optimization exemption on first run (phase 2).
- Distribution: sideloaded APK or Play internal-testing track — no store review needed.

## Phases

1. **Core loop** ✅ — manual start/stop, GPS foreground service, Room, history list,
   classify dialog, driver name, CSV export.
2. **Automation** ✅ — Bluetooth auto start/stop (pick car device from paired list,
   CompanionDeviceManager association for background FGS starts, one-tap "Driving?"
   notification as fallback), end-of-trip classification notification with
   [Work…]/[Personal]/[Passenger] actions, merge-into-previous-trip for short stops,
   settings screen with background location + battery exemption onboarding.
3. **Shared log** — Google Sign-In, Sheets sync via WorkManager (retry until landed),
   driver from account, second phone onboarded, duplicate-trip overlap detection
   (both drivers in the car).
4. **Polish** — NFC tag support, purpose autocomplete, per-financial-year export,
   monthly km-per-business summary, home-screen widget.
