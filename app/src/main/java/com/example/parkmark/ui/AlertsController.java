package com.example.parkmark.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.parkmark.R;
import com.example.parkmark.data.*;
import com.example.parkmark.reminders.Reminders;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.function.Consumer;

// Bind the alert forms to persisted timer records and report permission limitations clearly.
public final class AlertsController {
    private final AppCompatActivity activity;
    private final ParkRepository repository;
    private final Consumer<String> message;
    private final Runnable requestNotifications;

    public AlertsController(AppCompatActivity activity, ParkRepository repository,
                            Consumer<String> message, Runnable requestNotifications) {
        this.activity = activity; this.repository = repository;
        this.message = message; this.requestNotifications = requestNotifications;
    }

    public void timer(ParkingState state) {
        if (state == null || state.current == null) { message.accept("Save a parking location first."); return; }
        long locationId = state.current.id;
        FormDialog form = new FormDialog(activity);
        boolean extending = state.timer != null && state.timer.expirationTime > System.currentTimeMillis();
        EditText minutes = form.field(extending ? "Minutes to add (1–10080)" : "Parking duration in minutes (1–10080)",
                "60", InputType.TYPE_CLASS_NUMBER, 5);
        EditText lead = form.field("Remind me this many minutes before expiry", "10", InputType.TYPE_CLASS_NUMBER, 5);
        CheckBox notifications = new CheckBox(activity);
        notifications.setText("Send parking reminders");
        notifications.setChecked(state.timer == null || state.timer.notificationEnabled);
        form.content.addView(notifications);
        TextView note = new TextView(activity);
        note.setText("This records your parking deadline. Pay or extend parking with your parking provider separately.");
        form.content.addView(note);
        form.show(extending ? "Extend parking timer" : "Set parking timer", "Save", () -> {
            int duration; int reminder;
            try { duration = Integer.parseInt(minutes.getText().toString()); reminder = Integer.parseInt(lead.getText().toString()); }
            catch (NumberFormatException error) { minutes.setError("Enter whole numbers."); return false; }
            if (duration < 1 || duration > 10080) { minutes.setError("Choose between 1 minute and 7 days."); return false; }
            long now = System.currentTimeMillis();
            long base = state.timer == null ? now : Math.max(now, state.timer.expirationTime);
            if (reminder < 0 || reminder * 60_000L >= base + duration * 60_000L - now) {
                lead.setError("Reminder must be earlier than expiry and later than now (0 = at expiry)."); return false;
            }
            boolean enabled = notifications.isChecked();
            repository.change(dao -> {
                ParkingLocation current = dao.current();
                if (current == null || current.id != locationId) throw new IllegalStateException("Parking changed");
                ParkingTimer timer = dao.timer(locationId);
                if (timer == null) { timer = new ParkingTimer(); timer.locationId = locationId; }
                timer.expirationTime = Math.max(System.currentTimeMillis(), timer.expirationTime) + duration * 60_000L;
                timer.reminderTime = timer.expirationTime - reminder * 60_000L;
                timer.notificationEnabled = enabled; timer.reminderDelivered = false; timer.expirationDelivered = false;
                if (timer.id == 0) dao.insertTimer(timer); else dao.updateTimer(timer);
                Reminders.clearNotifications(activity, "timer", locationId);
            }, error -> {
                message.accept(error == null ? "Parking timer saved." : "Could not save the timer.");
                if (error == null && enabled) requestNotifications.run();
            });
            return true;
        });
    }

    public void cancelTimer(ParkingState state) {
        if (state == null || state.current == null || state.timer == null) return;
        long locationId = state.current.id;
        new MaterialAlertDialogBuilder(activity).setTitle("Cancel parking timer?")
                .setMessage("Your saved parking location and notes will be kept.")
                .setNegativeButton("Keep", null).setPositiveButton("Cancel timer", (dialog, which) -> repository.change(dao -> {
                    dao.deleteTimer(locationId); Reminders.cancel(activity, "timer", locationId);
                }, error -> message.accept(error == null ? "Timer canceled." : "Could not cancel the timer."))).show();
    }

    public void renderStatus(ParkingState state) {
        TextView status = activity.findViewById(R.id.reminderStatusText);
        if (status == null) return;
        status.setText(!Reminders.notificationsAvailable(activity)
                ? "Notifications are off. Enable them to receive parking and permit reminders."
                : !Reminders.preciseAlarmsAvailable(activity)
                ? "Android may delay reminders. Allow precise alarms in Reminder settings."
                : "Android allows notifications and precise alarms. Reminders follow each timer or permit’s settings.");
        activity.findViewById(R.id.cancelTimerButton).setEnabled(state != null && state.timer != null);
        TextView button = activity.findViewById(R.id.alertExtendTimeButton);
        button.setText(state != null && state.timer != null ? "Extend time" : "Set timer");
    }

    // Open platform settings only in response to the user's explicit settings action.
    public void settings() {
        new MaterialAlertDialogBuilder(activity).setTitle("Reminder settings")
                .setItems(new String[]{"Notifications", "Precise alarms"}, (dialog, which) -> {
                    Intent intent;
                    if (which == 1 && Build.VERSION.SDK_INT >= 31) {
                        intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.getPackageName()));
                    } else if (which == 0 && Build.VERSION.SDK_INT >= 26) {
                        intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                    } else {
                        intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
                    }
                    try { activity.startActivity(intent); }
                    catch (ActivityNotFoundException error) { message.accept("Open your device's app settings to manage reminders."); }
                }).setNegativeButton("Close", null).show();
    }
}
