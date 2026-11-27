package com.example.parkmark;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.room.Room;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;
import com.example.parkmark.data.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

// Exercise actual SQLite constraints and the upgrade path with Android's database engine.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ParkDatabaseTest {
    private ParkDatabase database;
    private ParkDao dao;
    private Context context;

    @Before public void openDatabase() {
        context = RuntimeEnvironment.getApplication();
        database = Room.inMemoryDatabaseBuilder(context, ParkDatabase.class).allowMainThreadQueries().build();
        dao = database.dao();
    }

    @After public void closeDatabase() { database.close(); }

    @Test public void legacyParkingImportsOnceAndPreservesPhoto() {
        SharedPreferences preferences = context.getSharedPreferences("test_import", 0);
        preferences.edit().clear()
                .putLong("latitude", Double.doubleToRawLongBits(0))
                .putLong("longitude", Double.doubleToRawLongBits(-81.2))
                .putString("parkingNotes", "Level 3")
                .putString("parkingPhotoUri", "content://test/photo")
                .putLong("parkingSavedTime", 1234).commit();
        database.runInTransaction(() -> LegacyParkingImporter.migrate(dao, preferences));
        database.runInTransaction(() -> LegacyParkingImporter.migrate(dao, preferences));
        assertEquals(1, dao.locations().size());
        assertEquals("Level 3", dao.current().notes);
        assertEquals("content://test/photo", dao.photos(dao.current().id).get(0).uri);
        preferences.edit().clear().commit();
    }

    @Test public void photosStayWithTheirLocationAndCascadeOnDelete() {
        dao.insertUser(new UserProfile());
        ParkingLocation first = new ParkingLocation();
        first.id = dao.insertLocation(first);
        ParkingLocation second = new ParkingLocation();
        second.id = dao.insertLocation(second);
        ParkingPhoto photo = new ParkingPhoto();
        photo.locationId = first.id;
        dao.insertPhoto(photo);
        assertEquals(1, dao.photos(first.id).size());
        assertTrue(dao.photos(second.id).isEmpty());
        dao.deleteLocation(first.id);
        assertTrue(dao.photos(first.id).isEmpty());
        assertNotNull(dao.location(second.id));
    }

    @Test public void onlyOneTimerCanBelongToEachLocation() {
        dao.insertUser(new UserProfile());
        long locationId = dao.insertLocation(new ParkingLocation());
        ParkingTimer timer = new ParkingTimer();
        timer.locationId = locationId;
        dao.insertTimer(timer);
        try {
            dao.insertTimer(timer);
            fail("A second timer must violate the unique location constraint");
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            assertNotNull(dao.timer(locationId));
        }
    }
    @Test public void locationAndPermitPersistAfterDatabaseReopens() {
        String name = "persistence-test.db";
        context.deleteDatabase(name);
        ParkDatabase fileDatabase = Room.databaseBuilder(context, ParkDatabase.class, name).allowMainThreadQueries().build();
        fileDatabase.dao().insertUser(new UserProfile());
        ParkingLocation location = new ParkingLocation(); location.address = "Garage B";
        fileDatabase.dao().insertLocation(location);
        ParkingPermit permit = new ParkingPermit(); permit.name = "UCF"; permit.expirationDate = 123456789;
        fileDatabase.dao().insertPermit(permit);
        fileDatabase.close();
        fileDatabase = Room.databaseBuilder(context, ParkDatabase.class, name).allowMainThreadQueries().build();
        assertEquals("Garage B", fileDatabase.dao().current().address);
        assertEquals("UCF", fileDatabase.dao().permits().get(0).name);
        assertEquals(123456789, fileDatabase.dao().permits().get(0).expirationDate);
        fileDatabase.close();
        context.deleteDatabase(name);
    }

}
