package com.example.parkmark.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.parkmark.data.ParkRepository;

// Re-read current database deadlines so a canceled or extended alarm can never send stale content.
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Reminders.ACTION.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) return;
        PendingResult pending = goAsync();
        ParkRepository.get(context).refresh(error -> pending.finish());
    }
}
