package com.example.parkmark.util;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

// Derive countdowns from absolute deadlines so closing the app never resets a timer.
public final class TimeText {
    private TimeText() { }

    public static String remaining(long deadline, long now) {
        if (deadline <= 0) return "No timer set";
        if (deadline <= now) return "Expired";
        long minutes = (deadline - now + 59_999) / 60_000;
        long hours = minutes / 60;
        return hours > 0 ? hours + "h " + minutes % 60 + "m left" : minutes + "m left";
    }

    public static String saved(long savedAt, long now) {
        long minutes = Math.max(0, now - savedAt) / 60_000;
        if (minutes == 0) return "Saved just now";
        if (minutes < 60) return "Saved " + minutes + "m ago";
        if (minutes < 1440) return "Saved " + minutes / 60 + "h " + minutes % 60 + "m ago";
        return "Saved " + minutes / 1440 + "d ago";
    }

    public static String dateTime(long time) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(time));
    }
}
