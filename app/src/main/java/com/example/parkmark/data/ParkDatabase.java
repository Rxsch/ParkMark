package com.example.parkmark.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// A single database enforces the USER, LOCATION, PHOTO, TIMER, and PERMIT relationships.
@Database(entities = {UserProfile.class, ParkingLocation.class, ParkingPhoto.class,
        ParkingTimer.class, ParkingPermit.class}, version = 1, exportSchema = true)
public abstract class ParkDatabase extends RoomDatabase {
    private static volatile ParkDatabase instance;
    public abstract ParkDao dao();

    public static ParkDatabase get(Context context) {
        if (instance == null) {
            synchronized (ParkDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            ParkDatabase.class, "parkmark.db").build();
                }
            }
        }
        return instance;
    }
}
