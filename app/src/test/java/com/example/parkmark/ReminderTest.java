package com.example.parkmark;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import androidx.room.Room;
import com.example.parkmark.data.*;
import com.example.parkmark.reminders.Reminders;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

// Test deadlines and deduplication against Room plus Android alarm/notification shadows.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReminderTest {
    private Context context;
    private ParkDatabase database;
    private ParkDao dao;
    private long locationId;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        database = Room.inMemoryDatabaseBuilder(context, ParkDatabase.class).allowMainThreadQueries().build();
        dao = database.dao();
        dao.insertUser(new UserProfile());
        locationId = dao.insertLocation(new ParkingLocation());
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        Shadows.shadowOf(context.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);
    }
    @After public void tearDown() { database.close(); }

    @Test public void schedulesFutureReminderAndExpiryWithoutSendingEarly() {
        ParkingTimer timer = new ParkingTimer();
        timer.locationId = locationId;
        timer.expirationTime = System.currentTimeMillis() + 3_600_000;
        timer.reminderTime = timer.expirationTime - 600_000;
        dao.insertTimer(timer);
        Reminders.reconcile(context, dao);
        assertEquals(2, Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
        assertEquals(0, Shadows.shadowOf(context.getSystemService(NotificationManager.class)).size());
    }

    @Test public void expiredTimerNotifiesOnceAndSkipsObsoleteWarning() {
        ParkingTimer timer = new ParkingTimer();
        timer.locationId = locationId;
        timer.expirationTime = System.currentTimeMillis() - 1000;
        timer.reminderTime = timer.expirationTime - 600_000;
        dao.insertTimer(timer);
        Reminders.reconcile(context, dao);
        assertTrue(dao.timer(locationId).expirationDelivered);
        assertTrue(dao.timer(locationId).reminderDelivered);
        assertEquals(1, Shadows.shadowOf(context.getSystemService(NotificationManager.class)).size());
        context.getSystemService(NotificationManager.class).cancelAll();
        Reminders.reconcile(context, dao);
        assertEquals(0, Shadows.shadowOf(context.getSystemService(NotificationManager.class)).size());
    }

    @Test public void finishingParkingCancelsBothAlarms() {
        ParkingTimer timer = new ParkingTimer();
        timer.locationId = locationId;
        timer.expirationTime = System.currentTimeMillis() + 3_600_000;
        timer.reminderTime = timer.expirationTime - 600_000;
        dao.insertTimer(timer);
        Reminders.reconcile(context, dao);
        dao.endCurrent();
        Reminders.reconcile(context, dao);
        assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().isEmpty());
    }

    @Test public void deniedNotificationsRemainUndeliveredUntilEnabled() {
        ParkingTimer timer = new ParkingTimer();
        timer.locationId = locationId;
        timer.expirationTime = System.currentTimeMillis() - 1000;
        timer.reminderTime = timer.expirationTime;
        dao.insertTimer(timer);
        Shadows.shadowOf(context.getSystemService(NotificationManager.class)).setNotificationsEnabled(false);
        Reminders.reconcile(context, dao);
        assertFalse(dao.timer(locationId).expirationDelivered);
        Shadows.shadowOf(context.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);
        Reminders.reconcile(context, dao);
        assertTrue(dao.timer(locationId).expirationDelivered);
    }
    @Test public void permitsHaveIndependentAlarmsAndOnlyTheDuePermitNotifies() {
        ParkingPermit soon = new ParkingPermit();
        soon.name = "UCF"; soon.expirationDate = System.currentTimeMillis() + 86_400_000;
        soon.reminderDate = System.currentTimeMillis() - 1000;
        soon.id = dao.insertPermit(soon);
        ParkingPermit later = new ParkingPermit();
        later.name = "City garage"; later.expirationDate = System.currentTimeMillis() + 7 * 86_400_000L;
        later.reminderDate = System.currentTimeMillis() + 6 * 86_400_000L;
        later.id = dao.insertPermit(later);
        Reminders.reconcile(context, dao);
        assertTrue(dao.permit(soon.id).reminderDelivered);
        assertFalse(dao.permit(later.id).reminderDelivered);
        assertEquals(1, Shadows.shadowOf(context.getSystemService(NotificationManager.class)).size());
        assertEquals(3, Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
    }

    @Test public void retentionDeletesOnlyTheExpiredPhoto() {
        ParkingPhoto expired = new ParkingPhoto();
        expired.locationId = locationId; expired.deleteAfter = System.currentTimeMillis() - 1000;
        dao.insertPhoto(expired);
        ParkingPhoto kept = new ParkingPhoto();
        kept.locationId = locationId; kept.uri = "photos/keep.jpg";
        dao.insertPhoto(kept);
        Reminders.reconcile(context, dao);
        assertEquals(1, dao.photos(locationId).size());
        assertEquals("photos/keep.jpg", dao.photos(locationId).get(0).uri);
    }

}
