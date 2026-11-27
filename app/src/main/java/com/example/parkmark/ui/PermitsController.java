package com.example.parkmark.ui;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.parkmark.R;
import com.example.parkmark.data.*;
import com.example.parkmark.reminders.Reminders;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// Each permit belongs to the local profile and has its own expiration and advance reminder.
public final class PermitsController {
    private final AppCompatActivity activity;
    private final ParkRepository repository;
    private final Consumer<String> message;
    private final Runnable requestNotifications;

    public PermitsController(AppCompatActivity activity, ParkRepository repository,
                             Consumer<String> message, Runnable requestNotifications) {
        this.activity = activity; this.repository = repository;
        this.message = message; this.requestNotifications = requestNotifications;
    }

    // Render every saved permit, including expired permits that can be renewed or removed.
    public void render(List<ParkingPermit> permits) {
        LinearLayout container = activity.findViewById(R.id.permitContainer);
        if (container == null) return;
        container.removeAllViews();
        if (permits.isEmpty()) {
            TextView empty = label("No parking permits saved. Add a permit to track its expiration.", 16);
            empty.setPadding(dp(8), dp(8), dp(8), dp(16));
            container.addView(empty);
        }
        for (ParkingPermit permit : permits) {
            MaterialCardView card = new MaterialCardView(activity);
            card.setCardBackgroundColor(Color.WHITE); card.setRadius(dp(20)); card.setCardElevation(0);
            card.setStrokeColor(Color.parseColor("#DFE1E6")); card.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(12);
            container.addView(card, cardParams);
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(16), dp(16), dp(12));
            card.addView(content);
            TextView title = label(permit.name, 20);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            content.addView(title);
            TextView remaining = label(remaining(permit.expirationDate), 22);
            remaining.setTextColor(Color.parseColor("#A84F00")); remaining.setPadding(0, dp(12), 0, dp(8));
            content.addView(remaining);
            content.addView(label("Expires: " + date(permit.expirationDate), 14));
            content.addView(label(permit.notificationEnabled ? "Reminder: " + date(permit.reminderDate) : "Reminders off", 14));
            LinearLayout buttons = new LinearLayout(activity);
            MaterialButton edit = new MaterialButton(activity); edit.setText("Edit / renew");
            edit.setOnClickListener(view -> edit(permit));
            MaterialButton delete = new MaterialButton(activity); delete.setText("Remove");
            delete.setOnClickListener(view -> remove(permit));
            buttons.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
            buttons.addView(delete, new LinearLayout.LayoutParams(0, -2, 1));
            content.addView(buttons);
        }
    }

    public void edit(ParkingPermit existing) {
        FormDialog form = new FormDialog(activity);
        EditText name = form.text("Permit name", existing == null ? "" : existing.name);
        Calendar selected = Calendar.getInstance();
        if (existing == null) selected.add(Calendar.YEAR, 1); else selected.setTimeInMillis(existing.expirationDate);
        // A selected expiration date is valid through the end of that day in the device's time zone.
        endOfDay(selected);
        TextView label = label("Expiration date (valid through this day)", 14);
        form.content.addView(label);
        MaterialButton expiry = new MaterialButton(activity); expiry.setText(date(selected.getTimeInMillis()));
        expiry.setOnClickListener(view -> {
            DatePickerDialog picker = new DatePickerDialog(activity, (datePicker, year, month, day) -> {
                selected.set(year, month, day); endOfDay(selected); expiry.setText(date(selected.getTimeInMillis()));
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
            picker.show();
        });
        form.content.addView(expiry);
        long days = existing == null ? 7 : Math.max(0, Math.round((existing.expirationDate - existing.reminderDate) / 86_400_000.0));
        EditText lead = form.field("Remind me this many days before expiry (0–3650)", String.valueOf(days), InputType.TYPE_CLASS_NUMBER, 4);
        CheckBox enabled = new CheckBox(activity); enabled.setText("Send permit reminders");
        enabled.setChecked(existing == null || existing.notificationEnabled); form.content.addView(enabled);
        form.content.addView(label("ParkMark tracks your permit. Purchase or renew the actual permit with your parking provider.", 14));
        form.show(existing == null ? "Add parking permit" : "Edit / renew permit", "Save", () -> {
            String permitName = name.getText().toString().trim();
            if (permitName.isEmpty()) { name.setError("Enter a permit name."); return false; }
            int reminderDays;
            try { reminderDays = Integer.parseInt(lead.getText().toString()); }
            catch (NumberFormatException error) { lead.setError("Enter a whole number of days."); return false; }
            if (reminderDays < 0 || reminderDays > 3650) { lead.setError("Enter a number from 0 to 3650."); return false; }
            long deadline = selected.getTimeInMillis();
            if (deadline <= System.currentTimeMillis()) { message.accept("Choose an expiration date of today or later."); return false; }
            Calendar reminder = (Calendar) selected.clone(); reminder.add(Calendar.DAY_OF_YEAR, -reminderDays);
            if (enabled.isChecked() && reminder.getTimeInMillis() <= System.currentTimeMillis()) {
                lead.setError("Choose fewer days so the reminder is still in the future."); return false;
            }
            boolean notifications = enabled.isChecked();
            long id = existing == null ? 0 : existing.id;
            repository.change(dao -> {
                ParkingPermit permit = id == 0 ? new ParkingPermit() : dao.permit(id);
                if (permit == null) throw new IllegalStateException("Permit no longer exists");
                permit.name = permitName; permit.expirationDate = deadline; permit.reminderDate = reminder.getTimeInMillis();
                permit.notificationEnabled = notifications; permit.reminderDelivered = false; permit.expirationDelivered = false;
                if (id == 0) dao.insertPermit(permit);
                else { dao.updatePermit(permit); Reminders.clearNotifications(activity, "permit", id); }
            }, error -> {
                message.accept(error == null ? "Parking permit saved." : "Could not save the permit.");
                if (error == null && notifications) requestNotifications.run();
            });
            return true;
        });
    }

    private void remove(ParkingPermit permit) {
        new MaterialAlertDialogBuilder(activity).setTitle("Remove " + permit.name + "?")
                .setMessage("Its saved record and reminders will be removed from ParkMark.")
                .setNegativeButton("Keep", null).setPositiveButton("Remove", (dialog, which) -> repository.change(dao -> {
                    dao.deletePermit(permit.id); Reminders.cancel(activity, "permit", permit.id);
                }, error -> message.accept(error == null ? "Permit removed." : "Could not remove the permit."))).show();
    }
    private TextView label(String value, int size) {
        TextView text = new TextView(activity); text.setText(value); text.setTextSize(size); text.setTextColor(Color.parseColor("#292929"));
        return text;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static String date(long value) { return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(value)); }
    private static void endOfDay(Calendar date) {
        date.set(Calendar.HOUR_OF_DAY, 23); date.set(Calendar.MINUTE, 59); date.set(Calendar.SECOND, 59); date.set(Calendar.MILLISECOND, 999);
    }
    private static String remaining(long expiry) {
        long now = System.currentTimeMillis();
        if (expiry <= now) return "Expired";
        Calendar today = Calendar.getInstance(); Calendar end = Calendar.getInstance(); end.setTimeInMillis(expiry);
        if (today.get(Calendar.YEAR) == end.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == end.get(Calendar.DAY_OF_YEAR)) return "Expires today";
        long days = Math.max(1, TimeUnit.MILLISECONDS.toDays(expiry - now));
        return days + (days == 1 ? " day left" : " days left");
    }
}
