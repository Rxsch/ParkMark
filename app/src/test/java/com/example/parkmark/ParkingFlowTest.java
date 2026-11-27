package com.example.parkmark;

import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import com.example.parkmark.data.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

// Drive the actual activity through tab changes and verify its database-backed state.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ParkingFlowTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    private void flush() throws Exception {
        ParkRepository.IO.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Before public void launch() throws Exception {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
        flush();
    }
    @After public void close() throws Exception {
        controller.pause().stop().destroy();
        flush();
    }
    private void tab(int id) {
        ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation)).setSelectedItemId(id);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void emptyTabsAreSafeAndShowNoFictitiousParking() throws Exception {
        assertEquals("No parking location saved", ((TextView) activity.findViewById(R.id.addressText)).getText().toString());
        tab(R.id.navParking);
        assertFalse(activity.findViewById(R.id.saveNotesButton).isEnabled());
        tab(R.id.navMap);
        assertFalse(activity.findViewById(R.id.navigateButton).isEnabled());
        tab(R.id.navTimer);
        assertFalse(activity.findViewById(R.id.cancelTimerButton).isEnabled());
    }

    @Test public void notesAndLevelSurviveTabChangesAndCanBeCleared() throws Exception {
        ParkRepository.get(activity).change(dao -> {
            ParkingLocation location = new ParkingLocation();
            location.latitude = 28.6; location.longitude = -81.2;
            location.address = "Garage A"; location.savedAt = System.currentTimeMillis();
            dao.insertLocation(location);
        }, null);
        flush();
        tab(R.id.navParking);
        ((EditText) activity.findViewById(R.id.notesInput)).setText("Near the elevator");
        ((EditText) activity.findViewById(R.id.levelInput)).setText("3");
        tab(R.id.navHome);
        flush();
        String summary = ((TextView) activity.findViewById(R.id.parkingDetailsText)).getText().toString();
        assertTrue(summary.contains("Near the elevator"));
        assertTrue(summary.contains("Level 3"));
        tab(R.id.navParking);
        ((EditText) activity.findViewById(R.id.notesInput)).setText("");
        activity.findViewById(R.id.saveNotesButton).performClick();
        flush();
        tab(R.id.navHome);
        assertFalse(((TextView) activity.findViewById(R.id.parkingDetailsText)).getText().toString().contains("Near the elevator"));
    }
}
