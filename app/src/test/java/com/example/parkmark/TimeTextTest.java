package com.example.parkmark;

import com.example.parkmark.util.TimeText;
import org.junit.Test;
import static org.junit.Assert.*;

// Verify expiration boundaries and countdown rounding independently of an activity lifecycle.
public class TimeTextTest {
    @Test public void countdownHandlesEmptyExpiredAndSubMinuteDeadlines() {
        assertEquals("No timer set", TimeText.remaining(0, 100));
        assertEquals("Expired", TimeText.remaining(100, 100));
        assertEquals("1m left", TimeText.remaining(101, 100));
        assertEquals("1h 0m left", TimeText.remaining(3_600_100, 100));
    }
    @Test public void countdownUsesThePersistedDeadlineAfterAnHourAway() {
        long deadline = 10_000_000;
        assertEquals("2h 0m left", TimeText.remaining(deadline, deadline - 7_200_000));
        assertEquals("1h 0m left", TimeText.remaining(deadline, deadline - 3_600_000));
    }
}
