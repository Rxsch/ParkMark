package com.example.parkmark.reminders;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.example.parkmark.MainActivity;
import com.example.parkmark.R;
import com.example.parkmark.data.*;
import com.example.parkmark.util.PhotoStorage;
import com.example.parkmark.util.TimeText;
import java.util.List;

// Reconcile alarms with persisted deadlines instead of depending on an activity or a running timer.
public final class Reminders {
    public static final String ACTION = "com.example.parkmark.REMINDER";
    public static final String CHANNEL = "parking_reminders";
    private Reminders() { }

    public static boolean notificationsAvailable(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = context.getSystemService(NotificationManager.class).getNotificationChannel(CHANNEL);
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }
    public static boolean preciseAlarmsAvailable(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    private static void channel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Parking reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Parking timer and permit expiration reminders");
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    // Called on the repository's single I/O executor after writes and whenever the app resumes.
    public static void reconcile(Context context, ParkDao dao) {
        channel(context);
        long now = System.currentTimeMillis();
        ParkingLocation current = dao.current();
        for (ParkingTimer timer : dao.allTimers()) {
            cancelAlarms(context, "timer", timer.locationId);
            if (current == null || current.id != timer.locationId || !timer.notificationEnabled) {
                clearNotifications(context, "timer", timer.locationId);
                continue;
            }
            if (timer.expirationTime <= now) {
                // A late wake-up should report expiry, never send an obsolete advance warning too.
                timer.reminderDelivered = true;
                if (!timer.expirationDelivered && notify(context, "timer", timer.locationId, true,
                        "Parking time expired", "Your parking timer ended. Check your parking spot.")) timer.expirationDelivered = true;
            } else {
                schedule(context, "timer", timer.locationId, true, timer.expirationTime);
                if (timer.reminderTime <= now && !timer.reminderDelivered) {
                    if (notify(context, "timer", timer.locationId, false, "Parking expires soon",
                            "Your parking expires at " + TimeText.dateTime(timer.expirationTime))) timer.reminderDelivered = true;
                } else if (!timer.reminderDelivered) schedule(context, "timer", timer.locationId, false, timer.reminderTime);
            }
            dao.updateTimer(timer);
        }
        reconcilePermits(context, dao, now);
        cleanPhotos(context, dao, now);
    }

    // Permit reminders share the same reboot-safe alarm mechanism as parking timers.
    private static void reconcilePermits(Context context, ParkDao dao, long now) {
        for (ParkingPermit permit : dao.permits()) {
            cancelAlarms(context, "permit", permit.id);
            if (!permit.notificationEnabled) { clearNotifications(context, "permit", permit.id); continue; }
            if (permit.expirationDate <= now) {
                permit.reminderDelivered = true;
                if (!permit.expirationDelivered && notify(context, "permit", permit.id, true,
                        "Parking permit expired", permit.name + " expired. Check with your parking provider.")) permit.expirationDelivered = true;
            } else {
                schedule(context, "permit", permit.id, true, permit.expirationDate);
                if (permit.reminderDate <= now && !permit.reminderDelivered) {
                    if (notify(context, "permit", permit.id, false, "Parking permit expires soon",
                            permit.name + " expires at " + TimeText.dateTime(permit.expirationDate))) permit.reminderDelivered = true;
                } else if (!permit.reminderDelivered) schedule(context, "permit", permit.id, false, permit.reminderDate);
            }
            dao.updatePermit(permit);
        }
    }

    // Honor optional photo retention dates and schedule the next private-file cleanup.
    private static void cleanPhotos(Context context, ParkDao dao, long now) {
        for (ParkingPhoto photo : dao.expiredPhotos(now)) {
            PhotoStorage.delete(context, photo.uri);
            dao.deletePhoto(photo.id);
        }
        context.getSystemService(AlarmManager.class).cancel(pending(context, "cleanup", 0, false));
        long next = dao.nextPhotoExpiry();
        if (next > now) schedule(context, "cleanup", 0, false, next);
    }

    private static PendingIntent pending(Context context, String type, long id, boolean expired) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION)
                .setData(Uri.parse("parkmark://reminder/" + type + "/" + id + "/" + expired));
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // Exact alarms are opt-in on recent Android; use the supported inexact fallback when denied.
    private static void schedule(Context context, String type, long id, boolean expired, long time) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent intent = pending(context, type, id, expired);
        try {
            if (preciseAlarmsAvailable(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent);
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent);
        } catch (SecurityException revoked) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent); }
    }
    private static void cancelAlarms(Context context, String type, long id) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        alarms.cancel(pending(context, type, id, false));
        alarms.cancel(pending(context, type, id, true));
    }
    public static void cancel(Context context, String type, long id) {
        cancelAlarms(context, type, id); clearNotifications(context, type, id);
    }
    public static void clearNotifications(Context context, String type, long id) {
        NotificationManagerCompat.from(context).cancel(type + ":" + id, 0);
        NotificationManagerCompat.from(context).cancel(type + ":" + id, 1);
    }

    // Delivery flags are written only after a notification is accepted, preventing duplicates on resume.
    private static boolean notify(Context context, String type, long id, boolean expired, String title, String message) {
        if (!notificationsAvailable(context)) return false;
        Intent launch = new Intent(context, MainActivity.class).putExtra("openAlerts", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 1, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (expired) NotificationManagerCompat.from(context).cancel(type + ":" + id, 0);
            NotificationManagerCompat.from(context).notify(type + ":" + id, expired ? 1 : 0,
                    new NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_timer)
                            .setContentTitle(title).setContentText(message)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                            .setContentIntent(content).setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_HIGH).build());
            return true;
        } catch (SecurityException denied) { return false; }
    }
}
