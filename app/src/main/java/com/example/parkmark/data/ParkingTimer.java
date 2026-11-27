package com.example.parkmark.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Store one record from the ParkMark design's relational model.
@Entity(tableName = "parking_timers", foreignKeys = @ForeignKey(
        entity = ParkingLocation.class, parentColumns = "id", childColumns = "locationId",
        onDelete = ForeignKey.CASCADE), indices = @Index(value = "locationId", unique = true))
public class ParkingTimer {
    @PrimaryKey(autoGenerate = true) public long id;
    public long locationId;
    public long expirationTime;
    public long reminderTime;
    public boolean notificationEnabled = true;
    public boolean reminderDelivered;
    public boolean expirationDelivered;
}
