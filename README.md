# ParkMark

A Java Android app for remembering where you parked, adding photos and notes, finding your car, and tracking parking deadlines and permits. The four main screens follow the Checkpoint 3 design: **Home, Parking, Alerts, and Map**.

## Run in Android Studio

1. Clone this repository and open the **ParkMark root folder**, which contains `settings.gradle.kts`.
2. Use an Android Studio version compatible with **Android Gradle Plugin 9.4.1**. Select **JDK 21** as the Gradle JDK under Settings / Build, Execution, Deployment / Build Tools / Gradle. The project no longer forces a machine-specific Java 25 download.
3. In SDK Manager, install **Android SDK Platform 37.0**, **SDK Build-Tools 36.0.0**, and Platform-Tools. Let Gradle sync finish; the first build needs internet access to download dependencies.
4. Choose an Android device or AVD running **API 24 or later**. A Google Play / Google APIs emulator image supports Google location services and Maps. Enable device Location and set an emulator location before testing GPS.
5. Press **Run**. The app launches with an honest empty state; no sample address, permit, countdown, or car photo is saved automatically.

The same source works on Ubuntu, Windows, and macOS. Each computer keeps its own SDK path and Maps key in `local.properties`. That file is ignored by Git. Do not copy somebody else's SDK path.

### Embedded Google map

The embedded preview requires a Google Cloud project with **Maps SDK for Android enabled**, the required billing setup, and a restricted Android API key. Follow [Google's Maps SDK setup instructions](https://developers.google.com/maps/documentation/android-sdk/get-api-key).

Add this to your existing, untracked `local.properties`:

```properties
MAPS_API_KEY=your_own_maps_android_key
```

Alternatively, set the `MAPS_API_KEY` environment variable before building. Environment configuration takes precedence. Never put a real key in `local.properties.example` or commit `local.properties`.

Restrict the key to the package `com.example.parkmark` and the SHA-1 fingerprint printed by `./gradlew signingReport` (`.\gradlew.bat signingReport` on Windows). Each developer's debug certificate may have a different fingerprint. Configure each authorized fingerprint in Google Cloud.

Without a configured key the Map tab displays an explanatory message. Saving coordinates, notes, photos, timers, and permits still works. **Navigate to Car** opens walking directions in Google Maps, with a browser fallback. A configured but invalid key must be corrected in Google Cloud; inspect Logcat for Google Maps authorization errors.

## Features

| Screen / action | Behavior |
| --- | --- |
| Home location button | Saves a fresh GPS position as a new parking session and opens Parking for details. Previous sessions remain in history. |
| Parking details | Saves garage level, parking spot/row, and optional notes. Edits also persist when leaving the screen. Notes can be cleared. |
| Update Location | Refreshes the existing session's coordinates and address while preserving its details, photos, and timer. |
| Photos | Takes a camera photo or imports an image, stores a private resized copy, browses multiple photos, removes photos, and optionally deletes a copy after 1, 7, or 30 days. The original gallery image is never deleted. |
| Finish Parking | Ends the current session, cancels its alarms, and retains its history. |
| Set / Extend time | Saves a real expiration time, an advance reminder, and a per-timer notification preference. Extending adds minutes to a future deadline or starts from now if expired. |
| Alerts / Subscriptions | Adds, edits/renews, and removes multiple parking permits, each with its own expiry date and reminder. Dates remain saved after closing the app. |
| Map | Shows the current parked-car marker and opens walking directions to that exact coordinate. |
| Menu | Opens parking history (including previous photos and directions), a local profile, manual coordinates, and reminder settings. |

A timer or permit is a reminder record. It **does not purchase, pay for, or renew parking** with a parking provider.

## Data and architecture

- **Room / SQLite** stores `users`, `parking_locations`, `photos`, `parking_timers`, and `parking_permits`, with indexed foreign keys and cascading child relationships. A unique `locationId` on the timer table enforces at most one timer per location.
- A single **local profile** owns this device's locations and permits. The design does not specify a login/backend, so no online authentication or account synchronization is claimed.
- `ParkRepository` serializes writes on one background executor and publishes database snapshots through `LiveData`. The UI never opens a database on its main thread.
- `LegacyParkingImporter` imports a valid parking location, notes, save time, and photo URI from the earlier `ParkMarkParking` SharedPreferences once. Existing preferences are retained; new writes go to Room. Legacy gallery images still depend on the URI access granted by the old version; newly added images are copied privately.
- New photos live in `files/photos/`. Camera capture uses a `FileProvider` limited to `cache/camera/`; no broad photo-library permission is needed.
- `Reminders` reconciles persisted deadlines after writes, app resume, device reboot, app replacement, clock changes, and exact-alarm permission changes. Notification delivery flags prevent repeats. A delayed wake-up sends an expiry notice instead of an obsolete advance warning.
- `ParkingMap` manages the embedded map lifecycle as tabs change. GPS uses a cancellable fresh-location request; late results cannot replace a later manual selection. Address lookup is optional and falls back to valid coordinates when offline.
- Room's version 1 schema is checked in under `app/schemas`. Future schema changes should increment the database version and add a migration; destructive migration is not enabled.

Important code sections include English comments explaining their responsibilities.

## Reminder permissions

On Android 13+, allow notifications when saving a timer or permit. Use **Reminder settings → Notifications** if permission or the notification channel is disabled.

On Android 12+, **Reminder settings → Precise alarms** can grant exact timing. Without that access, ParkMark uses Android's inexact alarm fallback and displays a message that delivery can be delayed. Countdowns still use the saved absolute deadline. Android may restrict reminders while the app is force-stopped; reopen it to reconcile saved alarms. Do not rely on a notification as proof of paid parking.

## Build and verify

From the project root on Ubuntu/macOS:

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release output is **unsigned**; no release keystore or signing secret is included.
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`
- GitHub Actions runs the same checks and publishes the debug APK and test reports as workflow artifacts. CI builds use an empty Maps key.

The automated suite uses JUnit and Robolectric to test database relationships, legacy import, persistence after database reopen, countdown boundaries, alarm scheduling/deduplication/cancellation, independent permit reminders, photo retention/private-copy persistence, and actual activity tab/edit flows. Robolectric is not an AVD: real GPS, camera apps, permission dialogs, Google Maps tiles, and reboot delivery still need device testing. See [the demonstration checklist](docs/DEMO_CHECKLIST.md).
