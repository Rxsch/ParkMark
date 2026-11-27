# ParkMark demonstration and device checks

Use a Google Play / Google APIs AVD, or an Android phone. Configure a Maps SDK key and enable Location. For an emulator, use Extended Controls → Location to send a coordinate such as `28.6024, -81.2001`.

## Parking and data persistence

1. Launch a fresh installation. Visit all four tabs and verify no fabricated address, timer, permit, or car photo appears. Map navigation and parking-detail saves must be unavailable until a location is saved.
2. On Home, tap the location button. Grant precise or approximate location permission. Verify that Parking opens and displays a saved address or coordinates.
3. Enter a garage level, parking spot, and notes. Save. Switch Home → Parking and verify the details remain. Clear notes and save again to confirm empty notes are supported.
4. Add two photos, one from the camera and one from the picker. Browse Previous/Next. Close and reopen ParkMark; confirm both photos remain. Delete the source gallery image and confirm the imported private copy remains.
5. Move the emulator location and tap Update Location. Verify that the new coordinates appear on the map and that the session's existing details and photos remain.
6. Finish Parking, save a different location, then open Menu → Parking history. Check both sessions, their independent notes, historical photos, and walking directions.
7. Deny location permission or switch off GPS. Confirm the app explains the problem and offers manual coordinates without crashing. Test negative longitude and `0` as valid coordinate values; reject out-of-range coordinates.
8. Edit My profile and reopen the app to verify local persistence.

## Timers and notifications

1. Set a timer for **2 minutes**, with a reminder **1 minute** before expiry. Enable notifications and precise alarms from Reminder settings when available.
2. Check the countdown on both Home and Alerts. Put ParkMark in the background; verify an advance reminder and then an expiry notification. Tap the notification and verify it opens Alerts.
3. Extend an active timer, then verify the former deadline does not send a stale alert. Cancel a timer and verify neither notification is sent. Finishing Parking must also cancel its alarms.
4. Start a longer timer, close the app, reopen it, and check that the original absolute deadline is retained. Reboot the emulator and check reminder rescheduling.
5. Deny notification permission and exact alarm access separately. The app must still save the timer, display the permission/timing limitation, and keep a correct countdown. Re-enable permissions through Reminder settings.
6. Android force-stop suspends app alarms; after a force-stop test, reopen ParkMark before expecting reminders to resume.

## Parking permits and retention

1. Add at least two permits with different names, expiry dates, and reminder offsets. Verify both cards show their own values.
2. Edit/renew one permit and verify the other remains unchanged. Reject a blank name, a past expiration date, and an advance reminder already in the past.
3. Remove one permit and confirm its saved record and pending notifications disappear.
4. Completely close and reopen the app; verify the remaining permit, parking details, photos, and timer persist.
5. Set a photo retention date and verify it is displayed. Removing the private copy must never remove the original photo in the gallery. The automated retention test exercises the expiry cleanup without waiting a day.

## Maps and submission recording

1. With a valid key, verify the embedded map centers on the saved car marker after saving and updating parking.
2. Tap Navigate to Car and verify walking directions target the stored latitude/longitude. Test the browser fallback on a device without the Maps app.
3. Verify the UI with large text, the keyboard open, and a small phone screen. Bottom navigation should remain reachable and forms should scroll.
4. Record the app's actual screens and permission interactions on the AVD. Include adding data, closing/reopening, and showing persistence.
5. If your course requires separate project and recording ZIPs, exclude generated build folders, `local.properties`, real API keys, signing keys, and SDK folders from the project archive. Keep the Gradle wrapper and source files.

## Automated verification in this change

- Debug and unsigned release compilation were exercised in a Linux environment with JDK 21 and SDK 37.0.
- JUnit / Robolectric cover 15 tests: database/migration/persistence (4), reminders/permits/retention (6), countdowns (2), activity tab/details flows (2), and private photo storage (1).
- Android Lint runs without blocking errors. Remaining warnings include existing unused sample artwork and version-update suggestions.
- Live GPS, an external camera app, Google Maps authorization/tiles, reboot timing, and the AVD submission recording are not claimed as automated test coverage.
