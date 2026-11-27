package com.example.parkmark.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

// Keep SQL and ownership checks in one place; callers run these methods off the UI thread.
@Dao
public interface ParkDao {
    @Query("SELECT * FROM users WHERE id = 1") UserProfile getUser();
    @Insert void insertUser(UserProfile user);
    @Update void updateUser(UserProfile user);

    @Query("SELECT * FROM parking_locations WHERE userId = 1 ORDER BY savedAt DESC, id DESC")
    List<ParkingLocation> locations();
    @Query("SELECT * FROM parking_locations WHERE userId = 1 AND active = 1 LIMIT 1")
    ParkingLocation current();
    @Query("SELECT * FROM parking_locations WHERE id = :id AND userId = 1")
    ParkingLocation location(long id);
    @Query("UPDATE parking_locations SET active = 0 WHERE userId = 1") void endCurrent();
    @Insert long insertLocation(ParkingLocation location);
    @Update void updateLocation(ParkingLocation location);
    @Query("DELETE FROM parking_locations WHERE id = :id AND userId = 1") void deleteLocation(long id);

    @Query("SELECT * FROM photos WHERE locationId = :locationId ORDER BY createdAt DESC, id DESC")
    List<ParkingPhoto> photos(long locationId);
    @Query("SELECT * FROM photos WHERE deleteAfter > 0 AND deleteAfter <= :now")
    List<ParkingPhoto> expiredPhotos(long now);
    @Insert long insertPhoto(ParkingPhoto photo);
    @Query("DELETE FROM photos WHERE id = :id") void deletePhoto(long id);

    @Query("SELECT * FROM parking_timers WHERE locationId = :locationId")
    ParkingTimer timer(long locationId);
    @Query("SELECT t.* FROM parking_timers t INNER JOIN parking_locations p ON p.id = t.locationId WHERE p.active = 1 AND p.userId = 1")
    List<ParkingTimer> activeTimers();
    @Insert long insertTimer(ParkingTimer timer);
    @Update void updateTimer(ParkingTimer timer);
    @Query("DELETE FROM parking_timers WHERE locationId = :locationId") void deleteTimer(long locationId);

    @Query("SELECT * FROM parking_permits WHERE userId = 1 ORDER BY expirationDate, id")
    List<ParkingPermit> permits();
    @Query("SELECT * FROM parking_permits WHERE id = :id AND userId = 1")
    ParkingPermit permit(long id);
    @Insert long insertPermit(ParkingPermit permit);
    @Update void updatePermit(ParkingPermit permit);
    @Query("DELETE FROM parking_permits WHERE id = :id AND userId = 1") void deletePermit(long id);
}
